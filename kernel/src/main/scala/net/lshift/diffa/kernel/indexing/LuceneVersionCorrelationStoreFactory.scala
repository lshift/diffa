/**
 * Copyright (C) 2010-2011 LShift Ltd.
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

package net.lshift.diffa.kernel.indexing

import java.io.File
import net.lshift.diffa.kernel.differencing.VersionCorrelationStoreFactory
import scala.collection.mutable.HashMap
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import org.apache.commons.io.FileUtils
import org.apache.lucene.store.{SimpleFSDirectory, NIOFSDirectory}
import org.slf4j.LoggerFactory
import net.lshift.diffa.kernel.config.{DomainConfigStore, DiffaPairRef}

/**
 * Factory that creates LuceneVersionCorrelationStore instances.
 */
class LuceneVersionCorrelationStoreFactory(
  baseDir: String,
  configStore: SystemConfigStore,
  domainConfigStore: DomainConfigStore,
  diagnostics:DiagnosticsManager
) extends VersionCorrelationStoreFactory {

  import LuceneVersionCorrelationStoreFactory._

  private val stores = HashMap[DiffaPairRef, LuceneVersionCorrelationStore]()
  
  def apply(pair: DiffaPairRef) =
    stores.getOrElseUpdate(pair,
      new LuceneVersionCorrelationStore(pair, luceneDirectory(pair), configStore, domainConfigStore, diagnostics))

  private def directory(pair: DiffaPairRef) = {
    val dir = new File(baseDir)
    if (!dir.exists()) dir.mkdirs()
    new File(baseDir, pair.identifier)
  }

  private def luceneDirectory(pair: DiffaPairRef) =
    directoryClass.getConstructor(classOf[File]).newInstance(directory(pair))



  def remove(pair: DiffaPairRef) {
    close(pair)

    // No need to check if the pair/directory exists first, since the implementation of this method
    // performs the check internally.
    FileUtils.deleteDirectory(directory(pair))
  }

  def close(pair: DiffaPairRef) {
    if (stores.contains(pair)) {
      stores(pair).close()
      stores.remove(pair)
    }
  }

  def close() {
    stores.keys.foreach(close(_))
  }

}

object LuceneVersionCorrelationStoreFactory {

  val log = LoggerFactory.getLogger(classOf[LuceneVersionCorrelationStoreFactory])

  lazy val directoryClass = {
    val os = System.getProperty("os.name")
    if (os != null && os.toLowerCase.indexOf("win") >= 0) {
      log.warn("Detected Windows OS, so using SimpleFSDirectory implementation. "
             + "See http://lucene.apache.org/java/3_5_0/api/core/org/apache/lucene/store/FSDirectory.html for further details.")
      classOf[SimpleFSDirectory]
    } else {
      classOf[NIOFSDirectory]
    }
  }
}