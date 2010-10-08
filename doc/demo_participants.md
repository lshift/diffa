---
layout: default
title: Demo Participants
---

{% include links.md %}

# Diffa Demo Application

This is a pre-packaged demo application that supplies two example participants, which, in themselves can be remote controlled from a web browser. It is useful to see how Diffa works in context without having to implement your own participants. In this article we're going to review the generic scenario in order to understand how the demo application is working. 

### Generic Diffa Layout

The moving parts in a generic deployment scenario will generally look like the following:

![generic]

In this diagram you can see the following components:

* The differencing agent;
* An upstream participant;
* A downstream participant;
* The user interface that allows you to view differences;

### Web Demo Application

As stated beforehand, if you just want to test drive Diffa, you don't want to have implement your own participants. The demo participant application is a small application that illustrates:

* How to add upstream events and create inconsistencies
* How to bring the downstream back into line with the upstream

The main difference between the demo application and a typical production rollout is a web application that contains two remote controllable Diffa participants. These participants are remote controllable for the sake of being able to view the whole end to end interaction within a single browser:

![demo_image]

In this example, the two participants are implemented by two different threads of execution within a single web application. In addition to this, a simple UI is provided to facilitate the manipulation of each participant's internal state. 

# Installing The Demo App

The demo app can either be run from the pre-built binary distribution or from source using Maven.

### Booting The Demo (From The Binary Package)

Surprisingly, the easy variant has not been documented yet.

### Booting The Demo (From Source)

Here's how you run the demo participants from source:

* Check out and build the latest version:

		$ mvn install -Dmaven.test.skip=true

* Once the installation phase has completed, boot the agent:

		$ cd agent
		$ mvn jetty:run
		.....
		..... // many lines omitted
		.....	
		2010-10-08 16:32:46.133:INFO::Started SelectChannelConnector@0.0.0.0:19093
		[INFO] Started Jetty Server
		[INFO] Starting scanner at interval of 600 seconds.

* Now boot the participant web app:

		$ cd participants-web
		$ mvn jetty:run
		.....
		..... // many lines omitted
		.....
		2010-10-08 16:40:24.853:INFO::Started SelectChannelConnector@0.0.0.0:19293
		[INFO] Started Jetty Server
		[INFO] Starting scanner at interval of 600 seconds.

      
* When the demo app boots, it will register an appropriate configuration with the agent. You should see the result of this configuration in the output of the agent's shell:

		16:40:21.745 [qtp433194283-27] Configuration.scala:103 - Processing group declare/update request: mygroup
		16:40:21.891 [qtp433194283-22] Configuration.scala:43 - Processing endpoint declare/update request: b
		16:40:21.918 [qtp433194283-23] Configuration.scala:43 - Processing endpoint declare/update request: a
		16:40:21.948 [qtp433194283-24] Configuration.scala:87 - Processing pair declare/update request: WEB-1
		16:40:22.067 [qtp433194283-24] DefaultSessionManager.scala:236 - Execute difference report for pair WEB-1
	
# Experimenting With The Demo App 	
		
Once both the agent and the demo app have successfully booted, you can open up a browser tab for each app:

* The agent is available on http://localhost:19093/diffa-agent/. An agent that has just booted and has no conflicts to report will look like this:

![1]

* The demo app is available on http://localhost:19093/diffa-agent/. The demo app has a panel to insert arbitrary data for fictitious pair of participants:

![2]


* Add an arbitrary (id, body) pair to the [upstream][] participant:

![6]

* Now switch back to the agent window - there you will see a blob appear that corresponds to the previously entered entity:

![5]

* Going back to the demo app, let's enter a corresponding entry into the [downstream][] participant panel. To illustrate the different types of differences, let's enter a value that is slightly different to the upstream's data item:

![7]

* Back in the agent window the detail of the blob will have changed from having a missing [downstream][] to having a different content:

![8]

* If we now line up the data item in the [downstream][] to match the [upstream][], the blob will disappear from the the agent window:

![9]

* The demo app also has a link that can be used to inject some historical data:

![10]

* The agent window will now display a list of conflicts: 

![11]
