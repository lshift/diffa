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

package net.lshift.diffa.kernel.matching

import net.lshift.diffa.kernel.events._
import collection.mutable.{HashMap, LinkedHashMap, ListBuffer}
import org.joda.time.DateTime
import org.slf4j.{Logger, LoggerFactory}
import net.lshift.diffa.kernel.config.{DiffaPair}
import net.lshift.diffa.kernel.frontend.{DomainPairDef, PairDef}

/**
 * Local, in memory, event matcher.
 */
class LocalEventMatcher(val pair:DomainPairDef, reaper:LocalEventMatcherReaper) extends EventMatcher {
  private val log:Logger = LoggerFactory.getLogger(getClass)

  val listeners = new ListBuffer[MatchingStatusListener]
  private val entries = new LinkedHashMap[TxnIdVsn, TxnDetail]
  private val inProgress = new HashMap[VersionID, Int]

  reaper.attachMatcher(this)

  def addListener(l: MatchingStatusListener) = {
    listeners.append(l)
  }

  def onChange(evt: PairChangeEvent, eventAckCallback: () => Unit) = {
    log.debug("Received event " + evt)

    val expiry = (new DateTime).plusSeconds(pair.matchingTimeout)
    val txnDetail = evt match {
      case UpstreamPairChangeEvent(id, _, _, vsn) => TxnDetail(TxnIdVsn(id, vsn, UpstreamSource), eventAckCallback, expiry)
      case DownstreamPairChangeEvent(id, _, _, vsn) => TxnDetail(TxnIdVsn(id, vsn, DownstreamSource), eventAckCallback, expiry)
      case DownstreamCorrelatedPairChangeEvent(id, _, _, uvsn, _) => TxnDetail(TxnIdVsn(id, uvsn, DownstreamSource), eventAckCallback, expiry)
    }

    // See if we have an event to match by trying to find the inverse of this event
    val possibleMatch = entries.synchronized { entries.remove(txnDetail.idVsn.inverse) }
    possibleMatch match {
      case None => {
        // This is a new event. Index it.
        enterTxn(txnDetail)
      }
      case Some(pair) => {
        // We've just paired these events. We can mark them as matched and drop them.
        onMatched(txnDetail, pair)
      }
    }
  }

  def isVersionIDActive(id: VersionID) = {
    inProgress.synchronized {
      inProgress.contains(id)
    }
  }

  def dispose = {
    log.debug("Disposing analyzer for pair: " + pair)
    reaper.detachMatcher(this)
  }

  def nextExpiring:Option[TxnDetail] = {
    entries.synchronized {
      entries.headOption match {
        case None         => None
        case Some((_, detail:TxnDetail))  => Some(detail)
      }
    }
  }

  def expireTxn(txnDetail:TxnDetail) {
    entries.synchronized {
      entries.remove(txnDetail.idVsn)
      versionLeft(txnDetail.idVsn.id)

      txnDetail.idVsn.source match {
        case UpstreamSource   => {
          log.debug("Processing event for expired upstream: " + txnDetail.idVsn.id + "; listeners = " + listeners.size)
          fireListeners(l => l.onUpstreamExpired(txnDetail.idVsn.id, txnDetail.idVsn.vsn))
        }
        case DownstreamSource => {
          log.debug("Processing event for expired downstream: " + txnDetail.idVsn.id + "; listeners = " + listeners.size)
          fireListeners(l => l.onDownstreamExpired(txnDetail.idVsn.id, txnDetail.idVsn.vsn))
        }
      }
      txnDetail.eventAckCallback()
    }
  }

  private def onMatched(a:TxnDetail, b:TxnDetail) = {
    log.debug("Processing event for matched pair: " + a.idVsn.id + "; listeners = " + listeners.size)

    // The entry that was previously stored is now no longer active
    versionLeft(a.idVsn.id)

    // Emit an event indicating the match, then ack both the messages
    fireListeners(l => l.onPaired(a.idVsn.id, a.idVsn.vsn))
    a.eventAckCallback()
    b.eventAckCallback()
  }
  private def fireListeners(f:(MatchingStatusListener) => Unit) = listeners.foreach(l => f(l))

  private def enterTxn(txnDetail:TxnDetail) = {
    entries.synchronized {
      entries(txnDetail.idVsn) = txnDetail
      versionEntered(txnDetail.idVsn.id)

      // If this is the first entry for the list, then should wake up the inform the reaper so it will monitor
      // this event.
      if (entries.size == 1) {
        reaper.notifyReaper
      }
    }
  }

  private def versionEntered(id: VersionID) {
    inProgress.synchronized {
      inProgress.get(id) match {
        case None => inProgress.put(id, 1)
        case Some(current) => inProgress.put(id, current + 1)
      }
    }
  }
  private def versionLeft(id: VersionID) {
    inProgress.synchronized {
      inProgress.get(id) match {
        case None => // Ignore
        case Some(1) => inProgress.remove(id)
        case Some(current) => inProgress.put(id, current - 1)
      }
    }
  }
}

abstract class TxnSource {
  def inverse:TxnSource
}
object UpstreamSource extends TxnSource {
  override def inverse = DownstreamSource
}
object DownstreamSource extends TxnSource {
  override def inverse = UpstreamSource
}

case class TxnIdVsn(id:VersionID, vsn:String, source:TxnSource) {
  def inverse = TxnIdVsn(id, vsn, source.inverse)
}

case class TxnDetail(idVsn:TxnIdVsn, eventAckCallback:Function0[Unit], expiry:DateTime) {
  def hasExpired(now:DateTime) = !expiry.isAfter(now)
}

class LocalEventMatcherReaper {
  private val log:Logger = LoggerFactory.getLogger(getClass)

  private val matchers = new ListBuffer[LocalEventMatcher]
  private var continueReaping = true
  private val reaperMon = new Object
  private val reaper = new Thread { override def run = reap }
  reaper.setName("LocalEventMatcher-Reaper")
  reaper.start

  def attachMatcher(m:LocalEventMatcher) = {
    matchers.synchronized {
      matchers += m
    }
    notifyReaper
  }

  def detachMatcher(m:LocalEventMatcher) = {
    matchers.synchronized {
      matchers -= m
    }
  }

  def dispose {
    log.debug("Disposing LocalEventReaper")

    reaperMon.synchronized {
      continueReaping = false
      reaperMon.notifyAll
    }
    reaper.join(10000)
  }

  def notifyReaper = reaperMon.synchronized { reaperMon.notifyAll }
  private def reap {
    while (true) {
      try {
        // Allow the reaper to be shut down
        reaperMon.synchronized {
          if (!continueReaping) return
        }

        val now = new DateTime
        var nextExpireTime:Option[DateTime] = None
        matchers.synchronized {
          // Retrieve the heads of each matcher, and find those expiring. Also work out the next expire time
          val heads = matchers.map(m => (m, m.nextExpiring)).foreach {
            case (m, Some(detail)) => {
//              log.debug("First expiring for " + m.pairKey + " is " + detail)

              if (detail.hasExpired(now)) {
//                log.debug("Expiring " + detail)
                m.expireTxn(detail)
                nextExpireTime = Some(now)      // Since we've expired something, we'll want to run the loop again
              } else {
                nextExpireTime match {
                  case None       => nextExpireTime = Some(detail.expiry)
                  case Some(best) => if (best.isAfter(detail.expiry)) nextExpireTime = Some(detail.expiry)
                }
              }
            }
            case (_, None) =>
          }
        }

        // Wait for an appropriate amount of time
//        log.debug("Using nextExpireTime of " + nextExpireTime)
        nextExpireTime match {
          case None      => reaperMon.synchronized { reaperMon.wait }
          case Some(t)   => {
            val waitMillis = t.getMillis - now.getMillis
            if (waitMillis > 0) {
//              log.debug("About to sleep " + waitMillis)

              reaperMon.synchronized {
                reaperMon.wait(waitMillis)
              }
            }
          }
        }
      } catch {
        case ex:Exception => log.error("Caught exception in reaper thread", ex)
      }
    }
  }
}