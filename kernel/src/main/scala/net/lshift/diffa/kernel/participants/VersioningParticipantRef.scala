package net.lshift.diffa.kernel.participants

import java.io.Closeable
import net.lshift.diffa.participant.correlation.ProcessingResponse

/**
 * Provides a reference to a version generation participant. This allows for version information to be
 * recovered in a correlated version system. An implementation of this will be provided via a
 * ScanningParticipantFactory implementation, and will generally be an accessor to a remote resource. The
 * implementation of this will be responsible for handling argument serialization, RPC execution and result
 * deserialization.
 */
trait VersioningParticipantRef {
  /**
   * Requests that the participant generate a processing response based on the given incoming entity data. The downstream
   * participant is not expected to re-admit the data into its system - it should simply parse the data so as to be able
   * to determine it's own version information.
   */
  def generateVersion(entityBody: String): ProcessingResponse
}

/**
 * Factory for creating version generation participants.
 */
trait VersioningParticipantFactory extends AddressDrivenFactory[VersioningParticipantRef]