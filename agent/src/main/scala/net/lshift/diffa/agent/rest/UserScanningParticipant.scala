package net.lshift.diffa.agent.rest

import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.participants.CategoryFunction
import net.lshift.diffa.kernel.config.User
import scala.collection.JavaConversions._
import net.lshift.diffa.kernel.frontend.SystemConfiguration
import net.lshift.diffa.participant.scanning.{ScanAggregation, ScanResultEntry, ScanConstraint}


/**
 * This class performs a very similar function to a derivation of
 * ScanningParticipantRef, but at this point, we're operating in terms of the
 * front-end interface, rather than being in the kernel.
 *
 * Currently this is called directly from net.lshift.diffa.agent.rest.UsersResource.
 */

class UserScanningParticipant (systemConfig: SystemConfiguration)  {

  def perform(constraints:Seq[ScanConstraint], aggregation:Seq[ScanAggregation]): Seq[ScanResultEntry] = {
    def generateVersion(user:User) = ScannableUtils.generateDigest(user.name, user.token)
    val users = ScannableUtils.filterByKey[User](systemConfig.listFullUsers, constraints, _.name)
    val scanResults = users.map  { u =>
      ScanResultEntry.forEntity(u.name, generateVersion(u), null, Map("name" -> u.name))
    }
    ScannableUtils.maybeAggregate(scanResults, aggregation)
  }

}
