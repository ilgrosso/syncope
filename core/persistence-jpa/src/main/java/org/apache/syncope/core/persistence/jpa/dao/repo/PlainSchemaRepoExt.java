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
package org.apache.syncope.core.persistence.jpa.dao.repo;

import java.util.Collection;
import java.util.List;
import org.apache.syncope.core.persistence.api.entity.AnyTypeClass;
import org.apache.syncope.core.persistence.api.entity.AnyUtils;
import org.apache.syncope.core.persistence.api.entity.PlainAttrValue;
import org.apache.syncope.core.persistence.api.entity.PlainSchema;

public interface PlainSchemaRepoExt {

    List<? extends PlainSchema> findByAnyTypeClasses(Collection<AnyTypeClass> anyTypeClasses);

    boolean hasAttrs(PlainSchema schema);

    boolean existsPlainAttrUniqueValue(String realmKey, PlainSchema schema, PlainAttrValue attrValue);

    boolean existsPlainAttrUniqueValue(AnyUtils anyUtils, String anyKey, PlainSchema schema, PlainAttrValue attrValue);

    PlainSchema save(PlainSchema schema);

    void deleteById(String key);
}
