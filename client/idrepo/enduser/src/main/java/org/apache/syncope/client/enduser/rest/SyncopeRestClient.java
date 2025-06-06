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
package org.apache.syncope.client.enduser.rest;

import java.util.List;
import org.apache.syncope.common.rest.api.service.SyncopeService;

public class SyncopeRestClient extends BaseRestClient {

    private static final long serialVersionUID = -2211371717449597247L;

    public List<String> searchUserTypeExtensions(final String groupName) {
        try {
            return getService(SyncopeService.class).readUserTypeExtension(groupName).getAuxClasses();
        } catch (Exception e) {
            LOG.debug("While reading any type classes for type extension of group {}", groupName, e);
            return List.of();
        }
    }
}
