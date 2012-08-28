package net.lshift.diffa.agent.rest

import javax.ws.rs._
import net.lshift.diffa.kernel.frontend.Configuration
import net.lshift.diffa.kernel.config.PairRef

class DomainServiceLimitsResource(val config:Configuration, val space:Long) {

  @GET
  @Path("/{name}")
  @Produces(Array("text/plain"))
  def effectiveDomainLimit(@PathParam("name") name:String) : String = {
    config.getEffectiveDomainLimit(space, name).toString
  }

  @GET
  @Path("/{pair}/{name}")
  @Produces(Array("text/plain"))
  def effectiveDomainLimit(@PathParam("pair") pair:String,
                           @PathParam("name") name:String) : String = {
    config.getEffectivePairLimit(PairRef(pair,space), name).toString
  }

  @PUT
  @Path("/{name}/hard")
  @Produces(Array("application/json"))
  def setDomainHardLimit(@PathParam("name") name:String,
                         value:String) = {
    config.setHardDomainLimit(space, name, value.toInt)
  }

  @PUT
  @Path("/{name}/default")
  @Produces(Array("application/json"))
  def setDomainDefaultLimit(@PathParam("name") name:String,
                         value:String) = {
    config.setDefaultDomainLimit(space, name, value.toInt)
  }

  @PUT
  @Path("/{pair}/{name}")
  @Produces(Array("application/json"))
  def setPairLimit(@PathParam("pair") pair:String,
                            @PathParam("name") name:String,
                            value:String) = {
    config.setPairLimit(PairRef(pair,space), name, value.toInt)
  }

}
