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
package net.lshift.diffa.kernel.scheduler

import java.io.Closeable
import org.quartz.impl.DirectSchedulerFactory
import org.quartz.jobs.NoOpJob
import org.quartz.listeners.TriggerListenerSupport
import java.lang.IllegalStateException
import net.lshift.diffa.kernel.util.AlertCodes
import org.slf4j.{LoggerFactory, Logger}
import net.lshift.diffa.kernel.differencing.DifferencesManager
import org.quartz.simpl.{RAMJobStore, SimpleThreadPool}
import org.quartz._
import org.quartz.JobKey.jobKey
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.TriggerKey.triggerKey
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.JobBuilder.newJob
import net.lshift.diffa.kernel.actors.PairPolicyClient
import net.lshift.diffa.kernel.config.system.SystemConfigStore
import net.lshift.diffa.kernel.config.{DiffaPairRef, DomainConfigStore, DiffaPair}
import scala.collection.JavaConversions._
import reflect.BeanProperty

/**
 * Quartz backed implementation of the ScanScheduler.
 */
class QuartzScanScheduler(systemConfig:SystemConfigStore, pairPolicyClient:PairPolicyClient, name:String)
    extends ScanScheduler
    with Closeable {

  private val log:Logger = LoggerFactory.getLogger(getClass)

  private val VIEW_DATA_KEY = "view"

  // Create a QuartzScheduler, and attach a global listener to it to see when events are triggered. This is the easiest
  // way to get stateful invocations whereby we can call other bean components.
  private val scheduler = createScheduler()
  scheduler.start()
  scheduler.getListenerManager.addTriggerListener(new TriggerListenerSupport {
    override def triggerFired(trigger: Trigger, context: JobExecutionContext) {
      val pairId = trigger.getJobKey.getName
      val (domain,pairKey) = DiffaPair.fromIdentifier(pairId)
      val view = trigger.getJobDataMap.getString(VIEW_DATA_KEY)

      if (view == "") {
        log.info("%s: Starting scheduled scan for pair %s".format(AlertCodes.SCHEDULED_SCAN_STARTING, pairKey))
        try {
          pairPolicyClient.scanPair(DiffaPairRef(pairKey, domain), None)
        } catch {
            // Catch, log, and drop exceptions to prevent the scheduler trying to do any misfire handling
          case ex =>
            log.error("%s: Failed to start scheduled scan for %s".format(AlertCodes.SCHEDULED_SCAN_FAILURE, pairKey), ex)
        }
      } else {
        log.info("%s: Starting scheduled scan for view %s on pair %s".format(AlertCodes.SCHEDULED_SCAN_STARTING, view, pairKey))
        try {
          pairPolicyClient.scanPair(DiffaPairRef(pairKey, domain), Some(view))
        } catch {
            // Catch, log, and drop exceptions to prevent the scheduler trying to do any misfire handling
          case ex =>
            log.error("%s: Failed to start scheduled scan for view %s on pair %s".format(AlertCodes.SCHEDULED_SCAN_FAILURE, view, pairKey), ex)
        }
      }

    }
    def getName = "GlobalScanTriggerListener"
  })

  // Ensure that a trigger is registered for each pair on startup
  systemConfig.listPairs.foreach(onUpdatePair(_))

  def onUpdatePair(pair:DiffaPair) {
    val existingJob = jobForPair(pair)
    val jobId = jobIdentifier(pair)
    val jobName = jobId.toString

    def buildTriggerKey(view:String) = triggerKey(if (view != null) jobName + "%" + view else jobName)

    def assertSchedule(oldTrigger:Option[CronTrigger],  view:String, cron:String) {
      // Don't reschedule if nothing has changed
      if (oldTrigger.isDefined && oldTrigger.get.getCronExpression == cron) return;

      if (view != "") {
        log.debug("Applying schedule '%s' for view %s on pair %s".format(pair.scanCronSpec, view, jobName))
      } else {
        log.debug("Applying schedule '%s' for pair %s".format(pair.scanCronSpec, jobName))
      }
      val trigger = newTrigger()
        .withIdentity(buildTriggerKey(view))
        .withSchedule(cronSchedule(cron))
        .usingJobData(VIEW_DATA_KEY, view)
        .forJob(jobId)
        .build()

      if (oldTrigger.isDefined) scheduler.unscheduleJob(oldTrigger.get.getKey)
      scheduler.scheduleJob(trigger)
    }

    // Calculate the schedules that we're after
    val schedules:Map[String, String] =
      (if(pair.scanCronSpec != null) Map[String,String]("" -> pair.scanCronSpec) else Map[String,String]()) ++
      pair.views.filter(v => v.scanCronSpec != null).map(v => v.name -> v.scanCronSpec).toMap

    if (schedules.size == 0) {
      if (existingJob != null) {
        log.debug("Removing schedule for pair %s".format(jobName))
        scheduler.deleteJob(jobId)
      }
    } else {
       if (existingJob == null) {
         scheduler.addJob(newJob(classOf[ScanJob]).withIdentity(jobId).storeDurably().build(), true)
       }

      val currentTriggers:scala.collection.mutable.Map[TriggerKey, CronTrigger] =
        scala.collection.mutable.Map(scheduler.getTriggersOfJob(jobId).map(t => t.getKey -> t.asInstanceOf[CronTrigger]):_*)

      schedules.foreach { case (view, cron) =>
        val currentTrigger = currentTriggers.remove(buildTriggerKey(view))
        assertSchedule(currentTrigger, view, cron)
      }

      // Any left-over current triggers need to be removed, since they don't match an active schedule
      currentTriggers.keys.foreach(k => scheduler.unscheduleJob(k))
    }
  }

  def onDeletePair(pair:DiffaPair) {
    val existingJob = jobForPair(pair)
    if (existingJob != null) {
      scheduler.deleteJob(jobIdentifier(pair))
    }
    else {
      log.warn("Received pair delete event (%s) for non-existent job".format(pair))
    }
  }

  def close() {
    scheduler.shutdown()
  }

  private def jobForPair(pair:DiffaPair) = scheduler.getJobDetail(jobIdentifier(pair))
  private def jobIdentifier(pair:DiffaPair) = new JobKey(pair.identifier)
  private def createScheduler() = {
    val threadPool = new SimpleThreadPool(1, Thread.NORM_PRIORITY)
    threadPool.initialize()

    // Allocate the scheduler
    DirectSchedulerFactory.getInstance().createScheduler(name, name, threadPool, new RAMJobStore())

    // Retrieve it
    DirectSchedulerFactory.getInstance().getScheduler(name)
  }
}

/**
 * Job type that is scheduled. We derive NoOpJob since quartz 
 */
class ScanJob extends NoOpJob {
  @BeanProperty var view:String = null
}