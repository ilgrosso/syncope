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
package org.apache.syncope.client.ui.commons.layout;

import java.util.ArrayList;
import java.util.List;
import org.apache.syncope.client.ui.commons.wizards.any.AnyForm;
import org.apache.syncope.common.lib.to.AnyTO;

public abstract class AbstractAnyFormLayout<A extends AnyTO, F extends AnyForm<A>>
        extends AbstractAnyFormBaseLayout<A, F> {

    private static final long serialVersionUID = -6061683026789976508L;

    private final List<String> whichPlainAttrs = new ArrayList<>();

    private final List<String> whichDerAttrs = new ArrayList<>();

    public List<String> getWhichPlainAttrs() {
        return whichPlainAttrs;
    }

    public List<String> getWhichDerAttrs() {
        return whichDerAttrs;
    }
}
