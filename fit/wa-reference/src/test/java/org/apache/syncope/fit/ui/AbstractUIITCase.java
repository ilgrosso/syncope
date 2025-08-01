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
package org.apache.syncope.fit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.apache.syncope.common.lib.Attr;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.SyncopeConstants;
import org.apache.syncope.common.lib.policy.AttrReleasePolicyTO;
import org.apache.syncope.common.lib.policy.AuthPolicyTO;
import org.apache.syncope.common.lib.policy.DefaultAttrReleasePolicyConf;
import org.apache.syncope.common.lib.policy.DefaultAuthPolicyConf;
import org.apache.syncope.common.lib.request.UserCR;
import org.apache.syncope.common.lib.types.ClientExceptionType;
import org.apache.syncope.common.lib.types.PolicyType;
import org.apache.syncope.common.rest.api.RESTHeaders;
import org.apache.syncope.fit.AbstractITCase;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.FormElement;
import org.junit.jupiter.api.Test;

public abstract class AbstractUIITCase extends AbstractITCase {

    protected static AuthPolicyTO getAuthPolicy() {
        String syncopeAuthModule = "DefaultSyncopeAuthModule";
        String ldapAuthModule = "DefaultLDAPAuthModule";
        String description = "UI auth policy";

        return POLICY_SERVICE.list(PolicyType.AUTH).stream().
                map(AuthPolicyTO.class::cast).
                filter(policy -> description.equals(policy.getName())
                && policy.getConf() instanceof DefaultAuthPolicyConf
                && ((DefaultAuthPolicyConf) policy.getConf()).getAuthModules().contains(syncopeAuthModule)
                && ((DefaultAuthPolicyConf) policy.getConf()).getAuthModules().contains(ldapAuthModule)).
                findFirst().
                orElseGet(() -> {
                    DefaultAuthPolicyConf policyConf = new DefaultAuthPolicyConf();
                    policyConf.getAuthModules().add(syncopeAuthModule);
                    policyConf.getAuthModules().add(ldapAuthModule);

                    AuthPolicyTO policy = new AuthPolicyTO();
                    policy.setName(description);
                    policy.setConf(policyConf);

                    Response response = POLICY_SERVICE.create(PolicyType.AUTH, policy);
                    if (response.getStatusInfo().getStatusCode() != Response.Status.CREATED.getStatusCode()) {
                        fail("Could not create Test Auth Policy");
                    }

                    return POLICY_SERVICE.read(
                            PolicyType.AUTH,
                            response.getHeaderString(RESTHeaders.RESOURCE_KEY));
                });
    }

    protected static AttrReleasePolicyTO getAttrReleasePolicy() {
        String stubAttrRepo = "DefaultStubAttrRepo";
        String description = "UI attr release policy";

        return POLICY_SERVICE.list(PolicyType.ATTR_RELEASE).stream().
                map(AttrReleasePolicyTO.class::cast).
                filter(policy -> description.equals(policy.getName())
                && policy.getConf() instanceof DefaultAttrReleasePolicyConf
                && ((DefaultAttrReleasePolicyConf) policy.getConf()).getPrincipalAttrRepoConf().
                        getAttrRepos().contains(stubAttrRepo)).
                findFirst().
                orElseGet(() -> {
                    DefaultAttrReleasePolicyConf policyConf = new DefaultAttrReleasePolicyConf();
                    policyConf.getPrincipalAttrRepoConf().getAttrRepos().add(stubAttrRepo);
                    policyConf.getReleaseAttrs().put("attr1", "identifier");
                    policyConf.getReleaseAttrs().put("firstname", "given_name");
                    policyConf.getReleaseAttrs().put("surname", "family_name");
                    policyConf.getReleaseAttrs().put("fullname", "name");
                    policyConf.getReleaseAttrs().put("email", "email");

                    AttrReleasePolicyTO policy = new AttrReleasePolicyTO();
                    policy.setName(description);
                    policy.setConf(policyConf);

                    Response response = POLICY_SERVICE.create(PolicyType.ATTR_RELEASE, policy);
                    if (response.getStatusInfo().getStatusCode() != Response.Status.CREATED.getStatusCode()) {
                        fail("Could not create Test Attr Release Policy");
                    }

                    return POLICY_SERVICE.read(
                            PolicyType.ATTR_RELEASE,
                            response.getHeaderString(RESTHeaders.RESOURCE_KEY));
                });
    }

    protected static String extractWACSRF(final String body) {
        FormElement form = (FormElement) Jsoup.parse(body).body().getElementsByTag("form").first();
        assertNotNull(form);

        Optional<String> execution = form.formData().stream().
                filter(keyval -> "_csrf".equals(keyval.key())).
                map(Connection.KeyVal::value).
                findFirst();
        assertTrue(execution.isPresent());

        return execution.get();
    }

    protected abstract void sso(String baseURL, String username, String password) throws IOException;

    @Test
    public void sso2Console() throws IOException {
        sso(CONSOLE_ADDRESS, "bellini", "password");
    }

    @Test
    public void sso2Enduser() throws IOException {
        sso(ENDUSER_ADDRESS, "bellini", "password");
    }

    protected abstract void passwordManagement(String baseURL, String username, String password) throws IOException;

    protected void passwordManagement(final String baseURL) throws IOException {
        String key = null;
        try {
            UserCR userCR = new UserCR.Builder(SyncopeConstants.ROOT_REALM, "mustChangePassword")
                    .password("Password123!")
                    .mustChangePassword(true)
                    .plainAttr(new Attr.Builder("fullname").value("mustChangePassword").build())
                    .plainAttr(new Attr.Builder("userId").value("mustChangePassword@syncope.org").build())
                    .plainAttr(new Attr.Builder("surname").value("mustChangePassword").build()).build();

            Response response = USER_SERVICE.create(userCR);
            assertEquals(HttpStatus.SC_CREATED, response.getStatus());
            key = response.getHeaderString(RESTHeaders.RESOURCE_KEY);

            AbstractUIITCase.this.passwordManagement(baseURL, "mustChangePassword", "Password123!");
        } finally {
            Optional.ofNullable(key).ifPresent(USER_SERVICE::delete);
        }
    }

    @Test
    public void passwordManagementConsole() throws IOException {
        passwordManagement(CONSOLE_ADDRESS);
    }

    @Test
    public void passwordManagementEnduser() throws IOException {
        passwordManagement(ENDUSER_ADDRESS);
    }

    @Test
    public void createUnmatching() throws IOException {
        try {
            USER_SERVICE.delete("pullFromLDAP");
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.NotFound, e.getType());
        }

        sso(CONSOLE_ADDRESS, "pullFromLDAP", "Password123");

        assertNotNull(USER_SERVICE.read("pullFromLDAP"));
    }

    protected abstract void doSelfReg(Runnable runnable);

    @Test
    public void selfRegUnmatching() throws IOException {
        try {
            USER_SERVICE.delete("pullFromLDAP");
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.NotFound, e.getType());
        }

        doSelfReg(() -> {
            try {
                sso(ENDUSER_ADDRESS, "pullFromLDAP", "Password123");
            } catch (IOException e) {
                fail(e);
            }
        });

        try {
            USER_SERVICE.read("pullFromLDAP");
            fail();
        } catch (SyncopeClientException e) {
            assertEquals(ClientExceptionType.NotFound, e.getType());
        }
    }
}
