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

package net.lshift.diffa.agent.itest.support

import concurrent.SyncVar
import org.slf4j.LoggerFactory
import net.lshift.diffa.participant.content.ContentParticipantHandler
import net.lshift.diffa.participant.scanning.ScanningParticipantRequestHandler
import net.lshift.diffa.participant.correlation.VersioningParticipantHandler
import net.lshift.diffa.participants.{NoAuthentication, ParticipantRpcServer}

/**
 * Helper objects for creation of HTTP/AMQP RPC chain for remote-controlling participants
 */
trait Participants {

  val upstreamScanUrl: String
  val upstreamContentUrl: String

  val downstreamScanUrl: String
  val downstreamContentUrl: String
  val downstreamVersionUrl: String

  def startUpstreamServer(scanning:ScanningParticipantRequestHandler, content:ContentParticipantHandler)
  def startDownstreamServer(scanning:ScanningParticipantRequestHandler, content:ContentParticipantHandler, versioning:VersioningParticipantHandler)
}

class HttpParticipants(usPort: Int, dsPort: Int) extends Participants {

  val log = LoggerFactory.getLogger(getClass)

  val upstreamUrl = "http://localhost:" + usPort
  val upstreamScanUrl = upstreamUrl + "/scan"
  val upstreamContentUrl = upstreamUrl + "/content"


  val downstreamUrl = "http://localhost:" + dsPort
  val downstreamScanUrl = downstreamUrl + "/scan"
  val downstreamContentUrl = downstreamUrl + "/content"
  val downstreamVersionUrl = downstreamUrl + "/corr-version"

  def startUpstreamServer(scanning:ScanningParticipantRequestHandler, content:ContentParticipantHandler) =
    forkServer(usPort, scanning, content, null)

  def startDownstreamServer(scanning:ScanningParticipantRequestHandler, content:ContentParticipantHandler, versioning:VersioningParticipantHandler) =
    forkServer(dsPort, scanning, content, versioning)

  private def forkServer(port: Int, scanning:ScanningParticipantRequestHandler, content:ContentParticipantHandler, versioning:VersioningParticipantHandler) {
    val server = new ParticipantRpcServer(port, scanning, content, versioning, NoAuthentication)
    val startupSync = new SyncVar[Boolean]
    new Thread {
      override def run = {
        try {
          server.start
        }
        catch {
          case x:Exception => {
            log.error("Cannot start server on port: " + port)
            throw x
          }
        }
        startupSync.set(true)
      }
    }.start

    startupSync.get(5000) match {
      case None => throw new Exception("Forked server on " + port + " failed to start")
      case _    =>
    }
  }
}
