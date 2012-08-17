package net.lshift.diffa.kernel.config

import net.lshift.diffa.kernel.naming.CacheName._
import net.lshift.diffa.kernel.util.cache.CacheProvider
import net.lshift.diffa.schema.tables.Spaces.SPACES
import net.lshift.diffa.schema.jooq.DatabaseFacade
import net.lshift.diffa.kernel.lifecycle.DomainLifecycleAware
import net.lshift.diffa.kernel.util.MissingObjectException
import scala.collection.JavaConversions._

/**
 * Use of this class is not advised - please refactor.
 */
@Deprecated class SpacePathCache(jooq:DatabaseFacade, cacheProvider:CacheProvider)
  extends DomainLifecycleAware {

  /**
   * This is used to denote a cache miss - it makes the assumption that the sequence generation is always positive.
   */
  val NON_EXISTENT_SPACE = Space(id = -1)

  private val cachedSpacePaths = cacheProvider.getCachedMap[String, Space](SPACE_PATHS)
  private val cachedReverseSpacePaths = cacheProvider.getCachedMap[Long, Space](REVERSE_SPACE_PATHS)

  def reset {
    cachedSpacePaths.evictAll()
    cachedReverseSpacePaths.evictAll()
  }

  def onDomainUpdated(space: Long) = evictCaches(space)
  def onDomainRemoved(space: Long) = evictCaches(space)

  private def evictCaches(id: Long) {
    val reverseCachedSpace = lookupSpace(id)
    if (reverseCachedSpace != NON_EXISTENT_SPACE) {
      cachedReverseSpacePaths.evict(reverseCachedSpace.id)
    }

    // TODO This is a very coarse grained invalidation - this can be done with greater precision
    cachedSpacePaths.evictAll()
  }

  def doesDomainExist(path: String) = resolveSpacePath(path) != NON_EXISTENT_SPACE

  def resolveSpacePathOrDie(path:String) = {
    val space = resolveSpacePath(path)
    if (space == NON_EXISTENT_SPACE) {
      throw new MissingObjectException(path)
    }
    else {
      space
    }
  }

  def resolveSpacePath(path:String) = {
    cachedSpacePaths.readThrough(path, () => jooq.execute(t => {
      val record = t.select().
        from(SPACES).
        where(SPACES.NAME.equal(path)).
        fetchOne()

      val space = if (record == null) {
        NON_EXISTENT_SPACE
      }
      else {
        Space(
          id = record.getValue(SPACES.ID),
          name = record.getValue(SPACES.NAME),
          configVersion = record.getValue(SPACES.CONFIG_VERSION)
        )
      }

      cachedReverseSpacePaths.put(space.id, space)

      space
    }))
  }

  def lookupSpace(id:Long) = {
    cachedReverseSpacePaths.readThrough(id, () => jooq.execute(t => {
      val record = t.select().
        from(SPACES).
        where(SPACES.ID.equal(id)).
        fetchOne()
      val space = if (record == null) {
        NON_EXISTENT_SPACE
      }
      else {
        Space(
          id = record.getValue(SPACES.ID),
          name = record.getValue(SPACES.NAME),
          configVersion = record.getValue(SPACES.CONFIG_VERSION)
        )
      }

      cachedSpacePaths.put(space.name, space)

      space
    }))
  }

}
