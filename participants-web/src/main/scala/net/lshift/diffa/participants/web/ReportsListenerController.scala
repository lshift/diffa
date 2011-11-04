package net.lshift.diffa.participants.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{RequestBody, RequestMethod, RequestMapping}

/**
 * Simple controller that receives and dumps reports.
 */
@Controller
@RequestMapping(Array("/reports"))
class ReportsListenerController {
  @RequestMapping(value=Array("display"), method=Array(RequestMethod.POST))
  def display(@RequestBody body:String) = {
    println("Got report: ")
    println(body)

    "ok"
  }
}