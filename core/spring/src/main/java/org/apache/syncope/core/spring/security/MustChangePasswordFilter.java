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
package org.apache.syncope.core.spring.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.apache.syncope.common.lib.types.IdRepoEntitlement;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;

public class MustChangePasswordFilter implements Filter {

    @Override
    public void init(final FilterConfig filterConfig) {
        // not used
    }

    @Override
    public void destroy() {
        // not used
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof SecurityContextHolderAwareRequestWrapper) {
            boolean isMustChangePassword =
                    SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(
                            authority -> IdRepoEntitlement.MUST_CHANGE_PASSWORD.equals(authority.getAuthority()));

            SecurityContextHolderAwareRequestWrapper wrappedRequest =
                    SecurityContextHolderAwareRequestWrapper.class.cast(request);
            if (isMustChangePassword && !"POST".equalsIgnoreCase(wrappedRequest.getMethod())
                    && !"/users/self/changePassword".equals(wrappedRequest.getPathInfo())) {

                throw new AccessDeniedException("Please change your password first");
            }
        }

        chain.doFilter(request, response);
    }
}
