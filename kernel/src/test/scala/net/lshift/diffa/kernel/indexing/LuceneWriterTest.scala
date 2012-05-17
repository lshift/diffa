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

import org.easymock.classextension.EasyMock._
import org.easymock.EasyMock.expect
import net.lshift.diffa.kernel.diag.DiagnosticsManager
import org.junit.{Before, Test}
import org.apache.lucene.store.Directory
import org.apache.lucene.index.{IndexReader, IndexWriter}
import java.io.Closeable

/**
 * Specifications of required behaviour of the LuceneWriter.
 */
class LuceneWriterTest {
  val index = createMock("index", classOf[Directory])

  val indexWriter = createMock("indexWriter", classOf[IndexWriter])
  val closeableIndexWriter = createMock("closeableIndexWriter", classOf[Closeable])

  val indexReader = createMock("indexReader", classOf[IndexReader])
  val closeableIndexReader = createMock("closeableIndexReader", classOf[Closeable])

  val diagnostics = createMock("diagnosticsManager", classOf[DiagnosticsManager])
  val luceneWriter = new LuceneWriter(index, diagnostics,
    new IndexWriterFactory {
      def createIndexWriter(index: Directory) = (closeableIndexWriter, indexWriter)
    },
    new IndexReaderFactory {
      def createIndexReader(writer: IndexWriter) = (closeableIndexReader, indexReader)
    })

  @Before
  def resetMocks {
    reset(closeableIndexReader, closeableIndexWriter)
  }

  @Test
  def indexReaderShouldBeClosedOnClose {
    expect(closeableIndexReader.close).once
    replay(closeableIndexReader)

    luceneWriter.close
    verify(closeableIndexReader)
  }

  @Test
  def indexWriterShouldBeClosedOnClose {
    expect(closeableIndexWriter.close).once
    replay(closeableIndexWriter)

    luceneWriter.close
    verify(closeableIndexWriter)
  }
}
