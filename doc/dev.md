---
layout: default
title: Development

---

{% include links.md %}

## Support

Support and general discussion lists about Diffa are accessible [here][support]. 

## Development

This is general information for those wanting to follow the development of Diffa.

## Roadmap

The roadmap is still quite vague at the moment but there is a vague interest in the following features:

* Authentication
* Configuration
* Scripting


## Get The Code

The code is available [on Github][repo].

## Submit An Issue

Please search the issue tracker before submitting a bug. The issue tracker is available [on Lighthouse][bugs].

### Building

If you'd like to build Diffa from source there is README in the top level of the source tree that gives a basic outline of how to do this. Diffa is built using Maven.

In order to download artifacts not available in Maven central, you can use the Diffa repository. To do so, 
add the following to your $M2_HOME/settings.xml:

* In the profiles block:
    
		<profile>
	      <id>diffa-repo</id>
	      <repositories>
	        <repository>
	          <id>diffa</id>
	          <url>https://nexus.lshift.net/nexus/content/groups/public</url>
	        </repository>
	      </repositories>
	
	      <pluginRepositories>
	        <pluginRepository>
	          <id>diffa</id>
	          <url>https://nexus.lshift.net/nexus/content/groups/public</url>
	        </pluginRepository>
	      </pluginRepositories>
	    </profile>

* In the activeProfiles block:
    
		<activeProfile>diffa-repo</activeProfile>

Once you have the proxy reference set up, then follow these steps: 

	$ git clone git://github.com/lshift/diffa.git
	$ cd diffa
	$ export MAVEN_OPTS=-XX:MaxPermSize=512m
	$ mvn install

After this has completed you can boot the agent using the Maven Jetty plugin:

	$ cd agent
	$ mvn jetty:run

Then the UI of agent should be available on http://localhost:19093/diffa-agent/