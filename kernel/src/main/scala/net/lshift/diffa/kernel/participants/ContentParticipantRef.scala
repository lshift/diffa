package net.lshift.diffa.kernel.participants

import java.io.Closeable

/**
 * Provides a reference to a content participant. An implementation of this will be provided via a
 * ContentParticipantFactory implementation, and will generally be an accessor to a remote resource. The
 * implementation of this will be responsible for handling argument serialization, RPC execution and result
 * deserialization.
 */trait ContentParticipantRef {
  /**
   * Requests that the participant return a serialized form of the item with the given identifier.
   */
  def retrieveContent(identifier:String): String
}

/**
 * Factory for creating content participant references.
 */
trait ContentParticipantFactory extends AddressDrivenFactory[ContentParticipantRef]