package net.lshift.diffa.kernel.indexing

import org.apache.lucene.store.Directory
import org.apache.lucene.util.Version
import org.apache.lucene.analysis.standard.StandardAnalyzer
import java.io.Closeable
import org.apache.lucene.index.{IndexReader, IndexWriterConfig, IndexWriter}

/**
 * Create an IndexWriter
 */
trait IndexWriterFactory {
  def createIndexWriter(index: Directory): (Closeable, IndexWriter)
}

object IndexWriterFactory {
  private val defaultWriterFactory = new IndexWriterFactory {
    def createIndexWriter(index: Directory) = {
      val version = Version.LUCENE_34
      val config = new IndexWriterConfig(version, new StandardAnalyzer(version))
      val writer = new IndexWriter(index, config)
      (writer, writer)
    }
  }

  def defaultFactory = defaultWriterFactory
}

/**
 * This is only needed in order to be able to verify that close is called on
 * IndexReader and IndexWriter.
 */
trait IndexReaderFactory {
  def createIndexReader(writer: IndexWriter): (Closeable, IndexReader)
}

object IndexReaderFactory {
  private val defaultReaderFactory = new IndexReaderFactory {
    def createIndexReader(writer: IndexWriter) = {
      val reader = IndexReader.open(writer, true)
      (reader, reader)
    }
  }
  
  def defaultFactory = defaultReaderFactory
}
