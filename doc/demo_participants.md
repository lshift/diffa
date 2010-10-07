---
layout: default
title: Demo Participants
---

{% include links.md %}

# Diffa Demo Application

This is a pre-packaged demo application that supplies two example participants, which, in themselves can be remote controlled from a web browser. It is useful to see how Diffa works in context without having to implement your own participants. In this article we're going to review the generic scenario in order to understand how the demo application is working. 

### Generic Diffa Layout

The generic deployment scenario will look like the following:

![generic]

In this diagram you can see the following components:

* The differencing agent;
* An upstream participant;
* A downstream participant;
* The user interface that allows you to view differences;
* A CLI toolset that can be used to configure the agent.

### Web Demo Application

As stated beforehand, if you just want to test drive Diffa, you don't want to have implement your own participants. The demo participant application is a small application that illustrates:

* How the continuous comparison works using our command line diff tail tool (which you are essentially porting to a browser based UI)
* How to add upstream events and create inconsistencies
* How to bring the downstream back into line with the upstream

The main difference between the demo application and a typical production rollout is a web application that contains two remote controllable Diffa participants. These participants are remote controllable for the sake of being able to view the whole end to end interaction within a single browser:

![demo]

In this example, the two participants are implemented by two different threads of execution within a single web application. In addition to this, a simple UI is provided to facilitate the manipulation of each participant's internal state. 

### Running The Demo

Here's how you run the demo participants:

* Check out and build the latest version (mvn install -Dmaven.test.skip=true)
* Boot the agent: in diffa/agent do $ mvn jetty:run
* Boot the participant web app: in diffa/participants-web do $ mvn jetty:run
* In diffa/tools run $ bash samples\declare-same.sh:

In the same window you should see this output:

	[INFO] [exec:java {execution: default-cli}]
	Declaring group: mygroup
	Declaring endpoint: a -> http://localhost:19293/diffa-participants/p/upstream
	Declaring endpoint: b -> http://localhost:19293/diffa-participants/p/downstream        
	Declaring pair: mygroup.WEB-1 -> (a <= {same} => b)

      
In the agent window you should see this output:

	15:38:40.257 [qtp1565082940-17] Configuration.scala:70 - Processing group declare/update request: mygroup
	15:38:40.348 [qtp1565082940-21] Configuration.scala:26 - Processing endpoint declare/update request: a
	15:38:40.374 [qtp1565082940-20] Configuration.scala:26 - Processing endpoint declare/update request: b
	15:38:40.400 [qtp1565082940-19] Configuration.scala:55 - Processing pair declare/update request: WEB-1
	15:38:40.493 [qtp1565082940-19] Alerter.scala:21 - FDK010: Starting analyzer for pair [WEB-1] with a window of 5 second(s)

* In diffa/tools run $ bash samples/diff-same.sh

In the agent window you should see this output: 

	15:40:04.225 [qtp1565082940-22] DifferencesResource.scala:34 - Creating a subscription for this pair: WEB-1
	15:40:04.700 [qtp1565082940-22] DefaultSessionManager.scala:78 - Created session cache for: net.lshift.diffa.kernel.differencing.LocalSessionCache@2bb4d74 for the pair key WEB-1

(tool window):

	[INFO] [exec:java {execution: default-cli}]

* Start playing with the UI
** Add an (id, version) to the upstream. This will emit a change event for the upstream. Just leave this for 5 seconds to leave the sliding window:

(agent window)

	15:41:50.028 [qtp1565082940-17] Changes.scala:20 - Received change event: UpstreamChangeEvent(VersionID(WEB-1,id),2010-09-21T15:41:49.810+01:00,version)
	15:41:50.036 [qtp1565082940-17] EsperMatcher.scala:101 - Received event UpstreamChangeEvent(VersionID(WEB-1,id),2010-09-21T15:41:49.810+01:00,version)
	15:41:54.951 [com.espertech.esper.Timer-WEB-1-Matcher-0] EsperMatcher.scala:40 - Processing event for expired upstream: id; listeners = 1

(tool window):

	> Difference: VersionID(WEB-1,id) (1)

* Add the same (id, version) to the upstream. This will rebalance the system for that same data item and the diff tool will show that the system has been aligned.

(agent window):

	15:43:57.731 [qtp1565082940-20] Changes.scala:20 - Received change event: DownstreamChangeEvent(VersionID(WEB-1,id),2010-09-21T15:43:57.719+01:00,version)
	15:43:57.740 [qtp1565082940-20] EsperMatcher.scala:101 - Received event DownstreamChangeEvent(VersionID(WEB-1,id),2010-09-21T15:43:57.719+01:00,version)
	15:44:02.651 [com.espertech.esper.Timer-WEB-1-Matcher-0] EsperMatcher.scala:46 - Processing event for expired downstream: id; listeners = 1

(tool window):

	> Alignment:  VersionID(WEB-1,id)
