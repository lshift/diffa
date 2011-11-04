package net.lshift.diffa.kernel.reporting

import org.restlet.resource.{Post, ServerResource}
import org.restlet.routing.Router
import org.restlet.Component
import org.restlet.data.Protocol
import collection.mutable.ListBuffer
import scala.collection.JavaConversions._

/**
 * Utility for creating an HTTP server that listens for reports.
 */
object ReportListenerUtil {
  def withReportListener(reports:ListBuffer[String], op: (String) => Unit) {
    val component = new Component

    try {
      component.getServers.add(Protocol.HTTP, 8123)
      component.getDefaultHost.attach("/report", new ReportListenerApplication(reports))
      component.start()
      op("http://localhost:8123/report/handle")
    } finally {
      component.stop()
    }
  }

  class ReportListenerApplication(reports:ListBuffer[String]) extends org.restlet.Application {
    override def createInboundRoot = {

      // Pass the report collector to the underlying resource
      // NOTE: This is due to Restlets API - it feels like they should provide a resource that you can constructor inject
      getContext.setAttributes(Map("reports"-> reports))

      val router = new Router(getContext)
      router.attach("/handle", classOf[HandleReportResource])
      router
    }
  }

  class HandleReportResource extends ServerResource {
    @Post def handleReport(content:String) = {
      val reports = getContext.getAttributes.get("reports").asInstanceOf[ListBuffer[String]]
      reports += content

      "Accepted"
    }
  }
}