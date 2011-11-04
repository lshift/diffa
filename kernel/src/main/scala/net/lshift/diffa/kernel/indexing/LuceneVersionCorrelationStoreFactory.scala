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
import org.apache.lucene.store.FSDirectory
import net.lshift.diffa.kernel.differencing.VersionCorrelationStoreFactory
import scala.collection.mutable.HashMap
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.DiffaPairRef
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import org.apache.commons.io.FileUtils

/**
 * Factory that creates LuceneVersionCorrelationStore instances.
 */
class LuceneVersionCorrelationStoreFactory[T <: FSDirectory](
  baseDir: String,
  directoryClass: Class[T],
  configStore: SystemConfigStore,
  diagnostics:DiagnosticsManager
) extends VersionCorrelationStoreFactory {

  private val stores = HashMap[DiffaPairRef, LuceneVersionCorrelationStore]()
  
  def apply(pair: DiffaPairRef) =
    stores.getOrElseUpdate(pair,
      new LuceneVersionCorrelationStore(pair, luceneDirectory(pair), configStore, diagnostics))

  private def directory(pair: DiffaPairRef) = new File(baseDir, pair.identifier)
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