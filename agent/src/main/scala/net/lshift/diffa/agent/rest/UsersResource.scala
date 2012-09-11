/**
 * Copyright (C) 2010-2012 LShift Ltd.
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
package net.lshift.diffa.agent.rest

import javax.ws.rs._
import core.{Request, Context, EntityTag}
import org.springframework.stereotype.Component
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.beans.factory.annotation.Autowired
import net.lshift.diffa.kernel.config.system.CachedSystemConfigStore
import com.sun.jersey.api.NotFoundException
import net.lshift.diffa.kernel.preferences.{UserPreferencesStore, FilteredItemType}
import net.lshift.diffa.kernel.config.PairRef
import org.springframework.security.access.PermissionEvaluator
import net.lshift.diffa.agent.rest.PermissionUtils._
import net.lshift.diffa.agent.auth.{SpaceTarget, UserTarget, Privileges}


@Path("/users/{user}/{domain}")
@Component
class UsersResource {

  @Autowired var systemConfigStore:CachedSystemConfigStore = null
  @Autowired var userPreferences:UserPreferencesStore = null
  @Autowired var permissionEvaluator:PermissionEvaluator = null

  @GET
  @Path("/filter/{itemType}")
  @Produces(Array("application/json"))
  def getFilters(@PathParam("user") user:String,
                 @PathParam("domain") domain:String,
                 @PathParam("itemType") itemType:String,
                 @Context request: Request) = {
    ensurePrivilege(permissionEvaluator, Privileges.USER_PREFERENCES, new UserTarget(user))

    val space = systemConfigStore.lookupSpaceByPath(domain)
    ensurePrivilege(permissionEvaluator, Privileges.SPACE_USER, new SpaceTarget(space.id))

    val filterType = getFilterType(itemType)
    val filters = userPreferences.listFilteredItems(space.id, user, filterType).toArray

    // This etag check doesn't prevent the call going through to the backend,
    // but that is not such a big deal since the result will be cached in any case.
    // Computing the etag and sending back a 304 can potentially cut down on network traffic

    val filtersVsn = new EntityTag(filters.hashCode().toString)

    request.evaluatePreconditions(filtersVsn) match {
      case null => // We'll continue with the request
      case r => throw new WebApplicationException(r.build)
    }

    filters
  }

  @PUT
  @Path("/{pair}/filter/{itemType}")
  def createFilter(@PathParam("user") user:String,
                   @PathParam("domain") domain:String,
                   @PathParam("pair") pair:String,
                   @PathParam("itemType") itemType:String) {
    ensurePrivilege(permissionEvaluator, Privileges.USER_PREFERENCES, new UserTarget(user))

    val space = systemConfigStore.lookupSpaceByPath(domain)
    ensurePrivilege(permissionEvaluator, Privileges.SPACE_USER, new SpaceTarget(space.id))

    val filterType = getFilterType(itemType)
    userPreferences.createFilteredItem(PairRef(pair,space.id), user, filterType)
  }

  @DELETE
  @Path("/{pair}/filter/{itemType}")
  def removeFilter(@PathParam("user") user:String,
                   @PathParam("domain") domain:String,
                   @PathParam("pair") pair:String,
                   @PathParam("itemType") itemType:String) {
    ensurePrivilege(permissionEvaluator, Privileges.USER_PREFERENCES, new UserTarget(user))

    val space = systemConfigStore.lookupSpaceByPath(domain)
    ensurePrivilege(permissionEvaluator, Privileges.SPACE_USER, new SpaceTarget(space.id))

    val filterType = getFilterType(itemType)
    userPreferences.removeFilteredItem(PairRef(pair,space.id), user, filterType)
  }

  private def getFilterType(unparsed:String) = {
    try {
      FilteredItemType.valueOf(unparsed)
    }
    catch {
      case x:IllegalArgumentException =>
        throw new InvalidEnumException("FilteredItemType", unparsed)
    }
  }

}
