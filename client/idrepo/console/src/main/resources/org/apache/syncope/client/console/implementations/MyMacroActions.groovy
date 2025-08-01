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
import groovy.transform.CompileStatic
import java.io.Serializable
import java.util.Map
import java.util.Optional
import jakarta.validation.ValidationException
import org.apache.syncope.common.lib.command.CommandArgs
import org.apache.syncope.common.lib.form.SyncopeForm
import org.apache.syncope.core.provisioning.api.macro.Command
import org.apache.syncope.core.provisioning.api.macro.MacroActions
import org.apache.syncope.core.provisioning.api.macro.Command.Result

@CompileStatic
class MyMacroActions implements MacroActions {

  @Override
  Optional<String> getDefaultValue(String formProperty) {
    return Optional.empty();
  }

  @Override
  Map<String, String> getDropdownValues(String formProperty) {
    return Map.of()
  }
  
  @Override
  void validate(SyncopeForm form, Map<String, Object> vars) throws ValidationException {
  }

  @Override
  void beforeAll(Map<String, Serializable> ctx) {
  }

  @Override
  void beforeCommand(Command<CommandArgs> command, CommandArgs args) {
  }

  @Override
  void afterCommand(Command<CommandArgs> command, CommandArgs args, Result result) {
  }

  @Override
  StringBuilder afterAll(Map<String, Serializable> ctx, StringBuilder output) {
    return output
  }
}
