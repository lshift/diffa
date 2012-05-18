package net.lshift.diffa.kernel.hooks

import net.lshift.diffa.kernel.differencing.OracleIndexRebuilder
import net.lshift.diffa.kernel.util.db.SessionHelper._
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.codec.digest.DigestUtils
import org.hibernate.{Session, SessionFactory}

/**
 * Oracle implementation of the DifferencePartitioningHook. Assumes that the underlying table has been list-partitioned
 * using a virtual column combining domain and pair key with an underscore (eg, 'diffa_pair1).
 */
class OracleDifferencePartitioningHook(sessionFactory:SessionFactory) extends DifferencePartitioningHook {
  def pairCreated(domain: String, key: String) {
    // Create the relevant partition
    sessionFactory.withSession(s => {
      val escapedVal = StringEscapeUtils.escapeSql(domain + "_" + key)
      val partitionName = generatePartitionName(domain, key)

      if (!hasPartition(s, partitionName)) {
        val query = s.createSQLQuery("alter table diffs add partition " + partitionName + " values('" + escapedVal + "')")
        query.executeUpdate()
        new OracleIndexRebuilder().rebuild(sessionFactory, "diffs")
      }
    })
  }

  def pairRemoved(domain: String, key: String) {
    // Drop the relevant partition on the diffs table
    sessionFactory.withSession(s => {
      val partitionName = generatePartitionName(domain, key)

      if (hasPartition(s, partitionName)) {
        val query = s.createSQLQuery("alter table diffs drop partition " + partitionName)
        query.executeUpdate()
        new OracleIndexRebuilder().rebuild(sessionFactory, "diffs")
      }
    })
  }

  def removeAllPairDifferences(domain: String, key: String) = {
    sessionFactory.withSession(s => {
      val partitionName = generatePartitionName(domain, key)

      if (hasPartition(s, partitionName)) {
        val query = s.createSQLQuery("alter table diffs truncate partition " + partitionName)
        query.executeUpdate()
        true
      } else {
        false
      }
    })
  }

  def isDifferencePartitioningEnabled = true

  /**
   * Generates a unique, repeatable partition name based on using the domain and key, preventing SQL-unsafe characters
   * from causing any kind of injection attack.
   */
  def generatePartitionName(domain:String, key:String) = "p_" + DigestUtils.md5Hex(domain + "_" + key).substring(0, 28)

  def hasPartition(s:Session, name:String) = {
    val query = s.createSQLQuery("select count(*) from user_tab_partitions where table_name='DIFFS' and partition_name=:name")
    query.setString("name", name.toUpperCase)   // Oracle will have forced the partition names to uppercase
    query.uniqueResult().asInstanceOf[java.math.BigDecimal].longValue() > 0
  }
}