/**
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.lshift.diffa.client

import java.io.InputStream

/**
 * DiffaHttpClient acts as an anti-corruption-layer so that:
 * 1) We have an easily mockable and relatively stateless interface for
 *   dealing with external HTTP-based services
 * 2) So that we can keep the technical details of dealing with the underlying
 * HTTP Library seperate from the diffa protocol logic.
 *
 * This does still leak some of the underlying implementation detail, as
 * reading the InputStream can still throw exceptions, eg: if the stream is
 * closed prematurely.
 */

trait DiffaHttpClient {
  def get(query:DiffaHttpQuery) : Either[Throwable, InputStream]

}