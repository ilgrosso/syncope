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
package org.apache.syncope.client.console.status;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.client.console.SyncopeConsoleSession;
import org.apache.syncope.client.ui.commons.Constants;
import org.apache.syncope.client.console.pages.BasePage;
import org.apache.syncope.client.console.panels.MultilevelPanel;
import org.apache.syncope.client.console.rest.ImplementationRestClient;
import org.apache.syncope.client.console.rest.RealmRestClient;
import org.apache.syncope.client.console.rest.ReconciliationRestClient;
import org.apache.syncope.client.ui.commons.markup.html.form.AjaxCheckBoxPanel;
import org.apache.syncope.client.ui.commons.markup.html.form.AjaxDropDownChoicePanel;
import org.apache.syncope.client.ui.commons.markup.html.form.AjaxPalettePanel;
import org.apache.syncope.common.lib.SyncopeConstants;
import org.apache.syncope.common.lib.to.EntityTO;
import org.apache.syncope.common.lib.to.ProvisioningTaskTO;
import org.apache.syncope.common.lib.to.PullTaskTO;
import org.apache.syncope.common.lib.to.PushTaskTO;
import org.apache.syncope.common.lib.types.IdMImplementationType;
import org.apache.syncope.common.lib.to.RealmTO;
import org.apache.syncope.common.lib.types.MatchingRule;
import org.apache.syncope.common.lib.types.UnmatchingRule;
import org.apache.syncope.common.rest.api.beans.ReconQuery;
import org.apache.wicket.PageReference;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.util.ListModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReconTaskPanel extends MultilevelPanel.SecondLevel {

    private static final long serialVersionUID = 5870444905957760434L;

    protected static final Logger LOG = LoggerFactory.getLogger(ReconTaskPanel.class);

    private final IModel<List<String>> pullActions = new LoadableDetachableModel<List<String>>() {

        private static final long serialVersionUID = 5275935387613157437L;

        @Override
        protected List<String> load() {
            return ImplementationRestClient.list(IdMImplementationType.PULL_ACTIONS).stream().
                    map(EntityTO::getKey).sorted().collect(Collectors.toList());
        }
    };

    private final IModel<List<String>> pushActions = new LoadableDetachableModel<List<String>>() {

        private static final long serialVersionUID = 5275935387613157437L;

        @Override
        protected List<String> load() {
            return ImplementationRestClient.list(IdMImplementationType.PUSH_ACTIONS).stream().
                    map(EntityTO::getKey).sorted().collect(Collectors.toList());
        }
    };

    public ReconTaskPanel(
            final String resource,
            final ProvisioningTaskTO taskTO,
            final String anyType,
            final String anyKey,
            final boolean isOnSyncope,
            final MultilevelPanel multiLevelPanelRef,
            final PageReference pageRef) {
        this(resource, taskTO, anyType, anyKey, null, isOnSyncope, multiLevelPanelRef, pageRef);
    }

    public ReconTaskPanel(
            final String resource,
            final ProvisioningTaskTO taskTO,
            final String anyType,
            final String anyKey,
            final String connObjectKeyValue,
            final boolean isOnSyncope,
            final MultilevelPanel multiLevelPanelRef,
            final PageReference pageRef) {

        Form<ProvisioningTaskTO> form = new Form<>("form", new CompoundPropertyModel<>(taskTO));
        add(form);

        if (taskTO instanceof PushTaskTO) {
            form.add(new Label("realm", ""));
        } else {
            AjaxDropDownChoicePanel<String> realm = new AjaxDropDownChoicePanel<>(
                    "realm", "destinationRealm", new PropertyModel<>(taskTO, "destinationRealm"), false);
            form.add(realm);
            realm.addRequiredLabel();
            realm.setOutputMarkupId(true);

            if (isOnSyncope) {
                realm.getField().setModelObject(SyncopeConstants.ROOT_REALM);
                realm.setVisible(false);
            } else {
                realm.setChoices(RealmRestClient.list().stream().
                        sorted(Comparator.comparing(RealmTO::getName)).
                        map(RealmTO::getFullPath).
                        collect(Collectors.toList()));
            }
        }

        AjaxPalettePanel<String> actions = new AjaxPalettePanel.Builder<String>().
                setAllowMoveAll(true).setAllowOrder(true).
                build("actions",
                        new PropertyModel<>(taskTO, "actions"),
                        new ListModel<>(taskTO instanceof PushTaskTO
                                ? pushActions.getObject() : pullActions.getObject()));
        actions.setOutputMarkupId(true);
        form.add(actions);

        AjaxDropDownChoicePanel<MatchingRule> matchingRule = new AjaxDropDownChoicePanel<>(
                "matchingRule", "matchingRule", new PropertyModel<>(taskTO, "matchingRule"), false);
        matchingRule.setChoices(List.of(MatchingRule.values()));
        form.add(matchingRule);

        AjaxDropDownChoicePanel<UnmatchingRule> unmatchingRule = new AjaxDropDownChoicePanel<>(
                "unmatchingRule", "unmatchingRule", new PropertyModel<>(taskTO, "unmatchingRule"),
                false);
        unmatchingRule.setChoices(List.of(UnmatchingRule.values()));
        form.add(unmatchingRule);

        taskTO.setPerformCreate(true);
        AjaxCheckBoxPanel performCreate = new AjaxCheckBoxPanel(
                "performCreate", "performCreate", new PropertyModel<>(taskTO, "performCreate"), false);
        form.add(performCreate);

        taskTO.setPerformUpdate(true);
        AjaxCheckBoxPanel performUpdate = new AjaxCheckBoxPanel(
                "performUpdate", "performUpdate", new PropertyModel<>(taskTO, "performUpdate"), false);
        form.add(performUpdate);

        taskTO.setPerformDelete(true);
        AjaxCheckBoxPanel performDelete = new AjaxCheckBoxPanel(
                "performDelete", "performDelete", new PropertyModel<>(taskTO, "performDelete"), false);
        form.add(performDelete);

        taskTO.setSyncStatus(true);
        AjaxCheckBoxPanel syncStatus = new AjaxCheckBoxPanel(
                "syncStatus", "syncStatus", new PropertyModel<>(taskTO, "syncStatus"), false);
        form.add(syncStatus);

        form.add(new AjaxSubmitLink("reconcile") {

            private static final long serialVersionUID = -817438685948164787L;

            @Override
            protected void onSubmit(final AjaxRequestTarget target) {
                ReconQuery reconQuery = new ReconQuery.Builder(anyType, resource).
                        anyKey(anyKey).
                        connObjectKeyValue(connObjectKeyValue).build();
                try {
                    if (taskTO instanceof PushTaskTO) {
                        ReconciliationRestClient.push(reconQuery, (PushTaskTO) form.getModelObject());
                    } else {
                        ReconciliationRestClient.pull(reconQuery, (PullTaskTO) form.getModelObject());
                    }

                    SyncopeConsoleSession.get().info(getString(Constants.OPERATION_SUCCEEDED));
                } catch (Exception e) {
                    LOG.error("While attempting reconciliation on {}", reconQuery, form.getModelObject(), e);
                    SyncopeConsoleSession.get().error(resource + ": "
                            + (StringUtils.isBlank(e.getMessage()) ? e.getClass().getName() : e.getMessage()));
                }
                multiLevelPanelRef.prev(target);
                ((BasePage) pageRef.getPage()).getNotificationPanel().refresh(target);
            }
        });
    }
}