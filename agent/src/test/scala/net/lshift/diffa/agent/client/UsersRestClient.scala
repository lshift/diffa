package net.lshift.diffa.agent.client

/**
 * Copyright (C) 2010-2011 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.lshift.diffa.kernel.frontend.UserDef
import net.lshift.diffa.client.{RestClientParams, AbstractRestClient}

class UsersRestClient(u:String, params: RestClientParams = RestClientParams.default)
  extends AbstractRestClient(u, "rest/security/", params) {

  def declareUser(user:UserDef) : UserDef = {
    create("users", user)
    user
  }
}