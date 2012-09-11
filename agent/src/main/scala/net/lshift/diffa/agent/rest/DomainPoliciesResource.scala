package net.lshift.diffa.agent.rest

import javax.ws.rs._
import net.lshift.diffa.kernel.config.system.{PolicyStatement, PolicyKey, SystemConfigStore}
import scala.collection.JavaConversions._
import net.lshift.diffa.agent.rest.ResponseUtils._

class DomainPoliciesResource(val systemConfigStore:SystemConfigStore, val space:Long) {

  @GET
  @Path("/{name}")
  @Produces(Array("application/json"))
  def retrievePolicy(@PathParam("name") name:String) : java.util.List[PolicyStatement] = {
    systemConfigStore.lookupPolicyStatements(PolicyKey(space, name)).toList
  }

  @PUT
  @Path("/{name}")
  @Consumes(Array("application/json"))
  def updatePolicy(@PathParam("name") name:String, statements:java.util.List[PolicyStatement]) = {
    systemConfigStore.storePolicy(PolicyKey(space, name), statements)
  }

  @DELETE
  @Path("/{name}")
  def removePolicy(@PathParam("name") name:String) = {
    systemConfigStore.removePolicy(PolicyKey(space, name))
    resourceDeleted()
  }
}
