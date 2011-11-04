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

package net.lshift.diffa.kernel.differencing

import reflect.BeanProperty
import org.joda.time.DateTime
import scala.collection.Map
import scala.collection.JavaConversions.MapWrapper
import scala.collection.JavaConversions.asMap
import collection.mutable.HashMap
import net.lshift.diffa.kernel.events.VersionID
import net.lshift.diffa.kernel.config.DiffaPairRef._
import net.lshift.diffa.kernel.config.{DiffaPairRef, Pair => DiffaPair}

// Base type for upstream and downstream correlations allowing pairs to be managed
case class Correlation(
  @BeanProperty var oid:java.lang.Integer = null,
  @BeanProperty var pairing:String = null,
  @BeanProperty var domain:String = null,
  @BeanProperty var id:String = null,
  var upstreamAttributes:Map[String,String] = null,
  var downstreamAttributes:Map[String,String] = null,
  @BeanProperty var lastUpdate:DateTime = null,
  @BeanProperty var timestamp:DateTime = null,
  @BeanProperty var storeVersion:java.lang.Long = null,
  @BeanProperty var upstreamVsn:String = null,
  @BeanProperty var downstreamUVsn:String = null,
  @BeanProperty var downstreamDVsn:String = null,
  @BeanProperty var isMatched:java.lang.Boolean = null
) {
  def this() = this(oid= null)
  def this(oid:java.lang.Integer,pair:DiffaPairRef,
           id:String,
           up:Map[String,String],
           down:Map[String,String],
           lastUpdate:DateTime, timestamp:DateTime,
           uvsn:String, duvsn:String, ddvsn:String,
           isMatched:java.lang.Boolean) = this(oid,pair.key,pair.domain,id,up,down,lastUpdate,timestamp,0L,uvsn,duvsn,ddvsn,isMatched)

  def this(oid:java.lang.Integer,pair:DiffaPairRef,
           id:String,
           up:Map[String,String],
           down:Map[String,String],
           lastUpdate:DateTime, timestamp:DateTime,
           storeVersion:java.lang.Long,
           uvsn:String, duvsn:String, ddvsn:String,
           isMatched:java.lang.Boolean) = this(oid,pair.key,pair.domain,id,up,down,lastUpdate,timestamp,storeVersion,uvsn,duvsn,ddvsn,isMatched)

  // Allocate these in the constructor because of NPE when Hibernate starts mapping this stuff 
  if (upstreamAttributes == null) upstreamAttributes = new HashMap[String,String]
  if (downstreamAttributes == null) downstreamAttributes = new HashMap[String,String]

  // TODO [#2] Can these proxies not be members of this class instead of being created on the stack?
  def getDownstreamAttributes() : java.util.Map[String,String] = {
    if (downstreamAttributes != null) {
      new MapWrapper[String,String](downstreamAttributes)
    } else {
      null
    }
  }

  def getUpstreamAttributes() : java.util.Map[String,String] = {
    if (upstreamAttributes != null) {
      new MapWrapper[String,String](upstreamAttributes)
    } else {
      null
    }
  }


  def setUpstreamAttributes(a:java.util.Map[String,String]) : Unit = upstreamAttributes = asMap(a)
  def setDownstreamAttributes(a:java.util.Map[String,String]) : Unit = downstreamAttributes = asMap(a)

  def asVersionID = VersionID(DiffaPairRef(pairing,domain),id)
}

object Correlation {
  def asDeleted(pair:DiffaPairRef, id:String, lastUpdate:DateTime) =
    Correlation(null, pair.key, pair.domain, id, null, null, lastUpdate, new DateTime, 0L, null, null, null, true)
  def asDeleted(id:VersionID, lastUpdate:DateTime) =
    Correlation(null, id.pair.key, id.pair.domain, id.id, null, null, lastUpdate, new DateTime, 0L, null, null, null, true)
}