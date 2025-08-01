/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.persistence.opensearch.dao;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.syncope.common.lib.SyncopeConstants;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.AttrSchemaType;
import org.apache.syncope.common.rest.api.service.JAXRSService;
import org.apache.syncope.core.persistence.api.attrvalue.PlainAttrValidationManager;
import org.apache.syncope.core.persistence.api.dao.AnyObjectDAO;
import org.apache.syncope.core.persistence.api.dao.DynRealmDAO;
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.PlainSchemaDAO;
import org.apache.syncope.core.persistence.api.dao.RealmSearchDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.dao.search.AnyCond;
import org.apache.syncope.core.persistence.api.dao.search.AnyTypeCond;
import org.apache.syncope.core.persistence.api.dao.search.AttrCond;
import org.apache.syncope.core.persistence.api.dao.search.AuxClassCond;
import org.apache.syncope.core.persistence.api.dao.search.DynRealmCond;
import org.apache.syncope.core.persistence.api.dao.search.MemberCond;
import org.apache.syncope.core.persistence.api.dao.search.MembershipCond;
import org.apache.syncope.core.persistence.api.dao.search.RelationshipCond;
import org.apache.syncope.core.persistence.api.dao.search.RelationshipTypeCond;
import org.apache.syncope.core.persistence.api.dao.search.ResourceCond;
import org.apache.syncope.core.persistence.api.dao.search.RoleCond;
import org.apache.syncope.core.persistence.api.dao.search.SearchCond;
import org.apache.syncope.core.persistence.api.entity.Any;
import org.apache.syncope.core.persistence.api.entity.AnyUtils;
import org.apache.syncope.core.persistence.api.entity.AnyUtilsFactory;
import org.apache.syncope.core.persistence.api.entity.EntityFactory;
import org.apache.syncope.core.persistence.api.entity.PlainAttrValue;
import org.apache.syncope.core.persistence.api.entity.PlainSchema;
import org.apache.syncope.core.persistence.api.entity.Realm;
import org.apache.syncope.core.persistence.api.utils.FormatUtils;
import org.apache.syncope.core.persistence.api.utils.RealmUtils;
import org.apache.syncope.core.persistence.common.dao.AbstractAnySearchDAO;
import org.apache.syncope.core.spring.security.AuthContextUtils;
import org.apache.syncope.ext.opensearch.client.OpenSearchUtils;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SearchType;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.DisMaxQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.CollectionUtils;

/**
 * Search engine implementation for users, groups and any objects, based on OpenSearch.
 */
public class OpenSearchAnySearchDAO extends AbstractAnySearchDAO {

    protected static final Set<String> ID_PROPS = Set.of("key", "id", "_id");

    protected final OpenSearchClient client;

    protected final int indexMaxResultWindow;

    public OpenSearchAnySearchDAO(
            final RealmSearchDAO realmSearchDAO,
            final DynRealmDAO dynRealmDAO,
            final UserDAO userDAO,
            final GroupDAO groupDAO,
            final AnyObjectDAO anyObjectDAO,
            final PlainSchemaDAO schemaDAO,
            final EntityFactory entityFactory,
            final AnyUtilsFactory anyUtilsFactory,
            final PlainAttrValidationManager validator,
            final OpenSearchClient client,
            final int indexMaxResultWindow) {

        super(
                realmSearchDAO,
                dynRealmDAO,
                userDAO,
                groupDAO,
                anyObjectDAO,
                schemaDAO,
                entityFactory,
                anyUtilsFactory,
                validator);

        this.client = client;
        this.indexMaxResultWindow = indexMaxResultWindow;
    }

    protected Triple<Optional<Query>, Set<String>, Set<String>> getAdminRealmsFilter(
            final Realm base,
            final boolean recursive,
            final Set<String> adminRealms,
            final AnyTypeKind kind) {

        Set<String> dynRealmKeys = new HashSet<>();
        Set<String> groupOwners = new HashSet<>();
        List<Query> queries = new ArrayList<>();

        if (recursive) {
            adminRealms.forEach(realmPath -> {
                Optional<Pair<String, String>> goRealm = RealmUtils.parseGroupOwnerRealm(realmPath);
                if (goRealm.isPresent()) {
                    groupOwners.add(goRealm.get().getRight());
                } else if (realmPath.startsWith("/")) {
                    Realm realm = realmSearchDAO.findByFullPath(realmPath).
                            orElseThrow(() -> new IllegalArgumentException("Invalid Realm full path: " + realmPath));

                    realmSearchDAO.findDescendants(realm.getFullPath(), base.getFullPath()).
                            forEach(descendant -> queries.add(
                            new Query.Builder().term(QueryBuilders.term().
                                    field("realm").value(FieldValue.of(descendant)).build()).
                                    build()));
                } else {
                    dynRealmDAO.findById(realmPath).ifPresentOrElse(
                            dynRealm -> {
                                dynRealmKeys.add(dynRealm.getKey());
                                queries.add(new Query.Builder().term(QueryBuilders.term().
                                        field("dynRealm").value(FieldValue.of(dynRealm.getKey())).build()).
                                        build());
                            },
                            () -> LOG.warn("Ignoring invalid dynamic realm {}", realmPath));
                }
            });
        } else {
            if (adminRealms.stream().anyMatch(r -> r.startsWith(base.getFullPath()))) {
                queries.add(new Query.Builder().term(QueryBuilders.term().
                        field("realm").value(FieldValue.of(base.getKey())).build()).
                        build());
            }
        }

        return Triple.of(
                dynRealmKeys.isEmpty() && groupOwners.isEmpty()
                ? Optional.of(new Query.Builder().disMax(QueryBuilders.disMax().queries(queries).build()).build())
                : Optional.empty(),
                dynRealmKeys,
                groupOwners);
    }

    protected Query getQuery(
            final Realm base,
            final boolean recursive,
            final Set<String> adminRealms,
            final SearchCond cond,
            final AnyTypeKind kind) {

        Query query;
        if (SyncopeConstants.FULL_ADMIN_REALMS.equals(adminRealms)) {
            query = getQuery(cond, kind);

            if (!recursive) {
                query = new Query.Builder().bool(
                        QueryBuilders.bool().
                                filter(new Query.Builder().term(QueryBuilders.term().
                                        field("realm").value(FieldValue.of(base.getKey())).build()).
                                        build()).
                                filter(query).build()).
                        build();
            }
        } else {
            Triple<Optional<Query>, Set<String>, Set<String>> filter =
                    getAdminRealmsFilter(base, recursive, adminRealms, kind);
            query = getQuery(buildEffectiveCond(cond, filter.getMiddle(), filter.getRight(), kind), kind);

            if (filter.getLeft().isPresent()) {
                query = new Query.Builder().bool(
                        QueryBuilders.bool().
                                filter(filter.getLeft().get()).
                                filter(query).build()).
                        build();
            }
        }

        return query;
    }

    @Override
    protected long doCount(
            final Realm base,
            final boolean recursive,
            final Set<String> adminRealms,
            final SearchCond cond,
            final AnyTypeKind kind) {

        CountRequest request = new CountRequest.Builder().
                index(OpenSearchUtils.getAnyIndex(AuthContextUtils.getDomain(), kind)).
                query(getQuery(base, recursive, adminRealms, cond, kind)).
                build();
        LOG.debug("Count request: {}", request);

        try {
            return client.count(request).count();
        } catch (Exception e) {
            LOG.error("While counting in OpenSearch with request {}", request, e);
            return 0;
        }
    }

    protected List<SortOptions> sortBuilders(final AnyTypeKind kind, final Stream<Sort.Order> orderBy) {
        AnyUtils anyUtils = anyUtilsFactory.getInstance(kind);

        List<SortOptions> options = new ArrayList<>();
        orderBy.forEach(clause -> {
            String sortName = null;

            String fieldName = clause.getProperty();
            // Cannot sort by internal _id
            if (!ID_PROPS.contains(fieldName)) {
                Field anyField = anyUtils.getField(fieldName).orElse(null);
                if (anyField == null) {
                    PlainSchema schema = plainSchemaDAO.findById(fieldName).orElse(null);
                    if (schema != null) {
                        sortName = fieldName;
                    }
                } else {
                    sortName = fieldName;
                }
            }

            if (sortName == null) {
                LOG.warn("Cannot build any valid clause from {}", clause);
            } else {
                options.add(new SortOptions.Builder().field(
                        new FieldSort.Builder().
                                field(sortName).
                                order(clause.getDirection() == Sort.Direction.ASC ? SortOrder.Asc : SortOrder.Desc).
                                build()).
                        build());
            }
        });
        return options;
    }

    @Override
    protected <T extends Any> List<T> doSearch(
            final Realm base,
            final boolean recursive,
            final Set<String> adminRealms,
            final SearchCond cond,
            final Pageable pageable,
            final AnyTypeKind kind) {

        SearchRequest request = new SearchRequest.Builder().
                index(OpenSearchUtils.getAnyIndex(AuthContextUtils.getDomain(), kind)).
                searchType(SearchType.QueryThenFetch).
                query(getQuery(base, recursive, adminRealms, cond, kind)).
                from(pageable.isUnpaged() ? 0 : pageable.getPageSize() * pageable.getPageNumber()).
                size(pageable.isUnpaged() ? indexMaxResultWindow : pageable.getPageSize()).
                sort(sortBuilders(kind, pageable.getSort().get())).
                fields(List.of()).source(new SourceConfig.Builder().fetch(false).build()).
                build();
        LOG.debug("Search request: {}", request);

        List<Hit<Void>> esResult = null;
        try {
            esResult = client.search(request, Void.class).hits().hits();
        } catch (Exception e) {
            LOG.error("While searching in OpenSearch with request {}", request, e);
        }

        return CollectionUtils.isEmpty(esResult)
                ? List.of()
                : buildResult(esResult.stream().map(Hit::id).collect(Collectors.toList()), kind);
    }

    protected Query getQuery(final SearchCond cond, final AnyTypeKind kind) {
        Query query = null;

        switch (cond.getType()) {
            case LEAF:
            case NOT_LEAF:
                query = cond.asLeaf(AnyTypeCond.class).
                        filter(leaf -> AnyTypeKind.ANY_OBJECT == kind).
                        map(this::getQuery).
                        orElse(null);

                if (query == null) {
                    query = cond.asLeaf(RelationshipTypeCond.class).
                            map(this::getQuery).
                            orElse(null);
                }

                if (query == null) {
                    query = cond.asLeaf(RelationshipCond.class).
                            map(this::getQuery).
                            orElse(null);
                }

                if (query == null) {
                    query = cond.asLeaf(MembershipCond.class).
                            filter(leaf -> AnyTypeKind.GROUP != kind).
                            map(this::getQuery).
                            orElse(null);
                }

                if (query == null) {
                    query = cond.asLeaf(MemberCond.class).
                            filter(leaf -> AnyTypeKind.GROUP == kind).
                            map(this::getQuery).
                            orElse(null);
                }

                if (query == null) {
                    query = cond.asLeaf(RoleCond.class).
                            filter(leaf -> AnyTypeKind.USER == kind).
                            map(this::getQuery).
                            orElse(null);
                }

                if (query == null) {
                    query = cond.asLeaf(DynRealmCond.class).
                            map(this::getQuery).
                            orElse(null);
                }

                if (query == null) {
                    query = cond.asLeaf(AuxClassCond.class).
                            map(this::getQuery).
                            orElse(null);
                }

                if (query == null) {
                    query = cond.asLeaf(ResourceCond.class).
                            map(this::getQuery).
                            orElse(null);
                }

                if (query == null) {
                    query = cond.asLeaf(AnyCond.class).map(ac -> getQuery(ac, kind)).
                            or(() -> cond.asLeaf(AttrCond.class).map(this::getQuery)).
                            orElse(null);
                }

                // allow for additional search conditions
                if (query == null) {
                    query = getQueryForCustomConds(cond, kind);
                }

                if (query == null) {
                    throw new IllegalArgumentException("Cannot construct QueryBuilder");
                }

                if (cond.getType() == SearchCond.Type.NOT_LEAF) {
                    query = new Query.Builder().bool(QueryBuilders.bool().mustNot(query).build()).build();
                }
                break;

            case AND:
                List<Query> andCompound = new ArrayList<>();

                Query andLeft = getQuery(cond.getLeft(), kind);
                if (andLeft._kind() == Query.Kind.Bool && !((BoolQuery) andLeft._get()).filter().isEmpty()) {
                    andCompound.addAll(((BoolQuery) andLeft._get()).filter());
                } else {
                    andCompound.add(andLeft);
                }

                Query andRight = getQuery(cond.getRight(), kind);
                if (andRight._kind() == Query.Kind.Bool && !((BoolQuery) andRight._get()).filter().isEmpty()) {
                    andCompound.addAll(((BoolQuery) andRight._get()).filter());
                } else {
                    andCompound.add(andRight);
                }

                query = new Query.Builder().bool(QueryBuilders.bool().filter(andCompound).build()).build();
                break;

            case OR:
                List<Query> orCompound = new ArrayList<>();

                Query orLeft = getQuery(cond.getLeft(), kind);
                if (orLeft._kind() == Query.Kind.DisMax) {
                    orCompound.addAll(((DisMaxQuery) orLeft._get()).queries());
                } else {
                    orCompound.add(orLeft);
                }

                Query orRight = getQuery(cond.getRight(), kind);
                if (orRight._kind() == Query.Kind.DisMax) {
                    orCompound.addAll(((DisMaxQuery) orRight._get()).queries());
                } else {
                    orCompound.add(orRight);
                }

                query = new Query.Builder().disMax(QueryBuilders.disMax().queries(orCompound).build()).build();
                break;

            default:
        }

        return query;
    }

    protected Query getQuery(final AnyTypeCond cond) {
        return new Query.Builder().term(QueryBuilders.term().
                field("anyType").value(FieldValue.of(cond.getAnyTypeKey())).build()).
                build();
    }

    protected Query getQuery(final RelationshipTypeCond cond) {
        return new Query.Builder().term(QueryBuilders.term().
                field("relationshipTypes").value(FieldValue.of(cond.getRelationshipTypeKey())).build()).
                build();
    }

    protected Query getQuery(final RelationshipCond cond) {
        List<Query> queries = check(cond).stream().
                map(key -> new Query.Builder().term(QueryBuilders.term().
                field("relationships").value(FieldValue.of(key)).build()).
                build()).toList();

        return queries.size() == 1
                ? queries.getFirst()
                : new Query.Builder().disMax(QueryBuilders.disMax().queries(queries).build()).build();
    }

    protected Query getQuery(final MembershipCond cond) {
        List<Query> queries = check(cond).stream().
                map(key -> new Query.Builder().term(QueryBuilders.term().
                field("memberships").value(FieldValue.of(key)).build()).
                build()).toList();

        return queries.size() == 1
                ? queries.getFirst()
                : new Query.Builder().disMax(QueryBuilders.disMax().queries(queries).build()).build();
    }

    protected Query getQuery(final RoleCond cond) {
        return new Query.Builder().term(QueryBuilders.term().
                field("roles").value(FieldValue.of(cond.getRole())).build()).
                build();
    }

    protected Query getQuery(final DynRealmCond cond) {
        return new Query.Builder().term(QueryBuilders.term().
                field("dynRealms").value(FieldValue.of(cond.getDynRealm())).build()).
                build();
    }

    protected Query getQuery(final MemberCond cond) {
        List<Query> queries = check(cond).stream().
                map(key -> new Query.Builder().term(QueryBuilders.term().
                field("members").value(FieldValue.of(key)).build()).
                build()).toList();

        return queries.size() == 1
                ? queries.getFirst()
                : new Query.Builder().disMax(QueryBuilders.disMax().queries(queries).build()).build();
    }

    protected Query getQuery(final AuxClassCond cond) {
        return new Query.Builder().term(QueryBuilders.term().
                field("auxClasses").value(FieldValue.of(cond.getAuxClass())).build()).
                build();
    }

    protected Query getQuery(final ResourceCond cond) {
        return new Query.Builder().term(QueryBuilders.term().
                field("resources").value(FieldValue.of(cond.getResource())).build()).
                build();
    }

    protected Query fillAttrQuery(
            final PlainSchema schema,
            final PlainAttrValue attrValue,
            final AttrCond cond) {

        Object value = schema.getType() == AttrSchemaType.Date && attrValue.getDateValue() != null
                ? FormatUtils.format(attrValue.getDateValue())
                : attrValue.getValue();

        Query query = null;

        switch (cond.getType()) {
            case ISNOTNULL:
                query = new Query.Builder().exists(QueryBuilders.exists().field(schema.getKey()).build()).build();
                break;

            case ISNULL:
                query = new Query.Builder().bool(QueryBuilders.bool().mustNot(
                        new Query.Builder().exists(QueryBuilders.exists().field(schema.getKey()).build()).build()).
                        build()).build();
                break;

            case ILIKE:
                StringBuilder output = new StringBuilder();
                for (char c : cond.getExpression().toLowerCase().replace("\\_", "_").toCharArray()) {
                    if (c == '%') {
                        output.append(".*");
                    } else if (Character.isLetter(c)) {
                        output.append('[').
                                append(c).
                                append(Character.toUpperCase(c)).
                                append(']');
                    } else {
                        output.append(OpenSearchUtils.escapeForLikeRegex(c));
                    }
                }
                query = new Query.Builder().regexp(QueryBuilders.regexp().
                        field(schema.getKey()).value(output.toString()).build()).build();
                break;

            case LIKE:
                query = new Query.Builder().wildcard(QueryBuilders.wildcard().
                        field(schema.getKey()).value(cond.getExpression().replace('%', '*').replace("\\_", "_")).
                        build()).build();
                break;

            case IEQ:
                query = new Query.Builder().match(QueryBuilders.match().
                        field(schema.getKey()).query(FieldValue.of(cond.getExpression().toLowerCase())).build()).
                        build();
                break;

            case EQ:
                FieldValue fieldValue;
                if (value instanceof Double aDouble) {
                    fieldValue = FieldValue.of(aDouble);
                } else if (value instanceof Long aLong) {
                    fieldValue = FieldValue.of(aLong);
                } else if (value instanceof Boolean aBoolean) {
                    fieldValue = FieldValue.of(aBoolean);
                } else {
                    fieldValue = FieldValue.of(value.toString());
                }
                query = new Query.Builder().term(QueryBuilders.term().
                        field(schema.getKey()).value(fieldValue).build()).
                        build();
                break;

            case GE:
                query = new Query.Builder().range(QueryBuilders.range().
                        field(schema.getKey()).gte(JsonData.of(value)).build()).
                        build();
                break;

            case GT:
                query = new Query.Builder().range(QueryBuilders.range().
                        field(schema.getKey()).gt(JsonData.of(value)).build()).
                        build();
                break;

            case LE:
                query = new Query.Builder().range(QueryBuilders.range().
                        field(schema.getKey()).lte(JsonData.of(value)).build()).
                        build();
                break;

            case LT:
                query = new Query.Builder().range(QueryBuilders.range().
                        field(schema.getKey()).lt(JsonData.of(value)).build()).
                        build();
                break;

            default:
        }

        return query;
    }

    protected Query getQuery(final AttrCond cond) {
        Pair<PlainSchema, PlainAttrValue> checked = check(cond);

        return fillAttrQuery(checked.getLeft(), checked.getRight(), cond);
    }

    @Override
    protected Triple<PlainSchema, PlainAttrValue, AnyCond> check(final AnyCond cond, final AnyTypeKind kind) {
        Triple<PlainSchema, PlainAttrValue, AnyCond> checked = super.check(cond, kind);

        // Manage difference between external id attribute and internal _id
        if ("id".equals(checked.getRight().getSchema())) {
            checked.getRight().setSchema("_id");
        }
        if ("id".equals(checked.getLeft().getKey())) {
            checked.getLeft().setKey("_id");
        }

        return checked;
    }

    protected Query getQuery(final AnyCond cond, final AnyTypeKind kind) {
        if (JAXRSService.PARAM_REALM.equals(cond.getSchema()) && cond.getExpression().startsWith("/")) {
            Realm realm = realmSearchDAO.findByFullPath(cond.getExpression()).
                    orElseThrow(() -> new IllegalArgumentException("Invalid Realm full path: " + cond.getExpression()));
            cond.setExpression(realm.getKey());
        }

        Triple<PlainSchema, PlainAttrValue, AnyCond> checked = check(cond, kind);

        return fillAttrQuery(checked.getLeft(), checked.getMiddle(), checked.getRight());
    }

    protected Query getQueryForCustomConds(final SearchCond cond, final AnyTypeKind kind) {
        return null;
    }
}
