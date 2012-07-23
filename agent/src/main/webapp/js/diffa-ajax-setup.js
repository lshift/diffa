/**
 * Copyright (C) 2012 LShift Ltd.
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

function addAuthToken(path) {
  /**
   * if there are existing query parameters, maintain them but set authToken.
   * if there are no query parameters, just append the authToken param.
   */
  var parts = path.split("?");
  var plainPath = parts[0];
  var query = parts[1];
  if (typeof USER_AUTH_TOKEN !== "undefined" && USER_AUTH_TOKEN.length > 0) {
    if (!query) {
      query = "authToken=" + USER_AUTH_TOKEN;
    } else {
      query = query + "&authToken=" + USER_AUTH_TOKEN;
    }
  }
  return plainPath + (!!query ? ("?" + query) : "");
};

$.ajaxSetup({
  beforeSend: function(jqXHR, settings) {
    if (settings.url[0] == '/') {
      settings.url = API_BASE + addAuthToken(settings.url);
    } else {
      settings.url = API_BASE + '/' + addAuthToken(settings.url);
    }
  }
});