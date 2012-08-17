package net.lshift.diffa.kernel.hooks

import net.lshift.diffa.kernel.differencing.OracleIndexRebuilder
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.codec.digest.DigestUtils
import net.lshift.diffa.schema.jooq.DatabaseFacade
import org.jooq.impl.Factory


/**
 * Oracle implementation of the DifferencePartitioningHook. Assumes that the underlying table has been list-partitioned
 * using a virtual column combining domain and pair key with an underscore (eg, 'diffa_pair1).
 */
class OracleDifferencePartitioningHook(jooq:DatabaseFacade) extends DifferencePartitioningHook {

  def pairCreated(space:Long, key: String) {

    val escapedVal = StringEscapeUtils.escapeSql(space + "_" + key)
    val partitionName = generatePartitionName(space, key)

    jooq.execute(t => {

      if (!hasPartition(t, partitionName)) {

        val query = "alter table diffs add partition " + partitionName + " values('" + escapedVal + "')"
        t.execute(query)

        new OracleIndexRebuilder().rebuild(t, "diffs")
      }
    })

  }

  def pairRemoved(space:Long, key: String) {

    val partitionName = generatePartitionName(space, key)

    // Drop the relevant partition on the diffs table
    jooq.execute(t => {

      if (hasPartition(t, partitionName)) {

        val query = "alter table diffs drop partition " + partitionName
        t.execute(query)

        new OracleIndexRebuilder().rebuild(t, "diffs")
      }

    })

  }

  def removeAllPairDifferences(space:Long, key: String) = {

    val partitionName = generatePartitionName(space, key)

    jooq.execute(t => {

      if (hasPartition(t, partitionName)) {
        val query = "alter table diffs truncate partition " + partitionName
        t.execute(query)
        new OracleIndexRebuilder().rebuild(t, "diffs")
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
  def generatePartitionName(space:Long, key:String) = "p_" + DigestUtils.md5Hex(space + "_" + key).substring(0, 28)

  def hasPartition(t:Factory, partition:String) = {

    val result = t.selectCount().
                   from("user_tab_partitions").
                   where("table_name = ?", "DIFFS").
                     and("partition_name = ?", partition.toUpperCase). // Oracle will have forced the partition names to uppercase
                   fetchOne()

    result.getValueAsBigDecimal(0).longValue() > 0

  }
}