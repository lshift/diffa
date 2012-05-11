package net.lshift.diffa.agent.rest



import scala.util.matching.Regex
import scala.collection.JavaConversions._
import org.junit.experimental.theories.{Theories, DataPoint,Theory}
import org.junit.runner.RunWith
import org.junit.Assert._
import org.easymock.EasyMock.{expect}
import org.easymock.classextension.EasyMock.{replay,createStrictMock,verify}
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.agent.rest.UserScanningParticipantTest.Scenario
import net.lshift.diffa.kernel.config.User
import org.junit.{Before, Test}
import net.lshift.diffa.kernel.participants.{StringPrefixCategoryFunction, CategoryFunction}
import net.lshift.diffa.participant.scanning.{StringPrefixConstraint, ScanConstraint, ScanResultEntry}
import net.lshift.diffa.kernel.frontend.{UserDef, SystemConfiguration}
import java.security.MessageDigest
import scala.Predef._
import org.apache.commons.codec.binary.Hex
import java.nio.charset.Charset


@RunWith(classOf[Theories])
class UserScanningParticipantTest {
  val systemConfig = createStrictMock("systemConfiguration", classOf[SystemConfiguration])

  var userScanningParticipant = new UserScanningParticipant(systemConfig)


  def setup(scenario: Scenario) {
    expect(systemConfig.listFullUsers).andReturn(scenario.userList).once()
    replay(systemConfig)
  }

  @Theory
  def shouldFetchUsersListFromConfigStore(scenario:Scenario) {
    setup(scenario)

    userScanningParticipant.perform(scenario.constraints, scenario.aggregation)

    verify(systemConfig)
  }

  @Theory
  def shouldAggregateUsers(scenario:Scenario) {
    setup(scenario)
    assertEquals(scenario.results,
      userScanningParticipant.perform(scenario.constraints, scenario.aggregation))
  }

}

object UserScanningParticipantTest {
  case class Scenario(userList:Seq[User],
                      constraints:Seq[ScanConstraint],
                      aggregation:Seq[CategoryFunction],
                      results:Seq[ScanResultEntry])

  def aggregatedVersionForUsers(matching_users: Seq[User]) = {
    val versions = matching_users.map { user =>
      ScannableUtils.generateDigest(user.name, user.token)
    }
    val md = MessageDigest.getInstance("MD5")
    versions.foreach { s => md.update(s.getBytes(Charset.forName("UTF-8"))) }
    new String (Hex.encodeHex(md.digest()))
  }

  val userAbc = User("abc")
  val userAbd = User("abd")
  val userBcd = User("bcd")

  @DataPoint def nullExample = Scenario(List(), List(), List(), List())

  @DataPoint def singleUserNoAggregation = Scenario(
    List(userAbc),
    List(),// Constraints
    List(), // Aggregation.
    List(ScanResultEntry.forEntity(userAbc.name,
      ScannableUtils.generateDigest(userAbc.name, userAbc.token),
      null, Map("name" -> userAbc.name)))
  )

  @DataPoint def twoUsersWithPrefixFilter = Scenario(
    List(userAbc, userBcd),
    List(new StringPrefixConstraint("name", "b")),
    List(), // Aggregation.
    List(ScanResultEntry.forEntity(userBcd.name,
      ScannableUtils.generateDigest(userBcd.name, userBcd.token),
      null, Map("name" -> userBcd.name)))
  )

  @DataPoint def twoUsersWithPrefixFilterAndAggregation = {
    val matching_users = List(userAbc, userAbd)

    val scanResult: ScanResultEntry = ScanResultEntry.forAggregate(
      aggregatedVersionForUsers(matching_users),
      Map("name" -> "a"))
    Scenario(
      List(userAbc, userAbd, userBcd),
      List(new StringPrefixConstraint("name", "a")),
      List(StringPrefixCategoryFunction("name", 1, 3, 1)),
      List(scanResult)
    )
  }


  @DataPoint def singleUserWithPrefixAggregation = {
    val matching_users: List[User] = List(userAbc)
    val scanResult = ScanResultEntry.forAggregate(
      aggregatedVersionForUsers(matching_users),
      Map("name" -> "a"))
    Scenario(
      List(userAbc),
      List(),// Constraints
      List(StringPrefixCategoryFunction("name", 1, 3, 1)),
      List(scanResult))
  }
}
