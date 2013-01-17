package net.lshift.diffa.kernel.config

import net.lshift.diffa.kernel.frontend.DomainEndpointDef


trait EndpointLifecycleListener {

  /**
   * Indicates that the given endpoint has become available (or has been updated).
   */
  def onEndpointAvailable(e:DomainEndpointDef)

  /**
   * Indicates that the endpoint with the given domain and name is no longer available within the system.
   */
  def onEndpointRemoved(space: Long, endpoint: String)
}
