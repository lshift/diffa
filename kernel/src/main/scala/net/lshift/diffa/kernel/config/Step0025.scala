package net.lshift.diffa.kernel.config

import org.hibernate.cfg.Configuration
import net.lshift.hibernate.migrations.MigrationBuilder

import scala.collection.JavaConversions._

/**
 * This Step migrates a schema/database to version 25, adding default read and
 * connect timeouts for scans.
 */
object Step0025 extends HibernateMigrationStep {
  def versionId = 25

  def name = "Add default scan timeouts"

  def createMigration(config: Configuration) = {
    val migration = new MigrationBuilder(config)

    List(
      (ServiceLimit.SCAN_CONNECT_TIMEOUT_KEY,
        "When attempting to open a connection to scan a participant, timeout after this many milliseconds",
        seconds_to_ms(30),
        minutes_to_ms(2)
        ),
      (ServiceLimit.SCAN_READ_TIMEOUT_KEY,
        "When reading query response data from a scan participant, timeout after not receiving data for this many milliseconds",
        seconds_to_ms(30),
        minutes_to_ms(2)
        )
    ) foreach { limDef =>
      val (name, desc, defaultTimeout, maxTimeout) = limDef

      migration.insert("limit_definitions").values(Map(
        "name" -> name,
        "description" -> desc
      ))

      migration.insert("system_limits").values(Map(
        "name" -> name,
        "default_limit" -> defaultTimeout,
        "hard_limit" -> maxTimeout
      ))
    }

    migration
  }

  private final val SEC_PER_MIN = 60
  private final val MS_PER_S = 1000

  /// Return type is java.lang.Integer since InsertBuilder.values requires a map of AnyRef
  private def minutes_to_ms(minutes: Int): java.lang.Integer = minutes * SEC_PER_MIN * MS_PER_S
  private def seconds_to_ms(seconds: Int): java.lang.Integer = seconds * MS_PER_S
}
