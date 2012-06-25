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

package net.lshift.diffa.agent.notifications

import net.lshift.diffa.kernel.config.User
import net.lshift.diffa.kernel.notifications.{NotificationEvent, NotificationProvider}
import org.slf4j.{Logger, LoggerFactory}
import javax.mail.internet.{MimeMessage, InternetAddress}
import javax.mail.{MessagingException, Message, Session}
import org.antlr.stringtemplate.StringTemplateGroup
import java.net.{URISyntaxException, URI}

class SmtpNotifier(val session:Session,
                   val ctx:String,
                   val host:String) extends NotificationProvider {

  val log:Logger = LoggerFactory.getLogger(getClass)

  val templateGroup = new StringTemplateGroup("mail")
  val bodyMaster = templateGroup.getInstanceOf("notifications/mail_body")
  val subjectMaster = templateGroup.getInstanceOf("notifications/mail_subject")

  var url:String = null

  try {
    url = new URI("http://" + host + "/" + ctx).toString    
  }
  catch {
    case e:URISyntaxException => log.error("Could not setup link, host = " + host + "; ctx = " + ctx)
  }

  log.debug("Setting the notification URL to: " + url)


  def notify(event:NotificationEvent, user:String) = {
  // TODO Delete this
  /*
    log.debug("Sending notification about " + event + " to " + user)
    try {

      val subject = subjectMaster.getInstanceOf
      subject.setAttribute("pairKey", event.id.pair.key)

      val body = bodyMaster.getInstanceOf
      body.setAttribute("pairKey", event.id.pair.key)
      body.setAttribute("entityId", event.id.id)
      body.setAttribute("timestamp", event.lastUpdated.toString())
      body.setAttribute("upstream", event.upstreamVsn)
      body.setAttribute("downstream", event.downstreamVsn)
      body.setAttribute("url", url)

      var to = new InternetAddress(user.email)

      val message = new MimeMessage(session)
      message.addRecipient(Message.RecipientType.TO, to)
      message.setSubject(subject.toString)
      message.setText(body.toString)

      session.getTransport.sendMessage(message, message.getAllRecipients)
    }
    catch {
      case m:MessagingException => {
        log.error("SMTP error: " + m.getMessage)
      }
      case n:NoSuchFieldException => {
        log.error("ST error: " + n.getMessage)
        val clazz = classOf[NotificationEvent]
        try {
          val field = clazz.getField("id")
        }
        catch {
          case e:Exception => {
            log.error("What is going on here?", e)        
          }
        }
      }
      case e:Exception => {
        log.error("Unknown error", e)        
      }
    }
    */
  }

}