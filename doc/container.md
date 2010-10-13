---
layout: default
title: Container Deployment

---

{% include links.md %}

# Container Deployment

Diffa can be deployed as standard Java web application into a servlet compliant container. At the moment, the only container that has been tested is Jetty. This guide shows you how to deploy Diffa using Jetty 7.1.6.v20100715.

## Configuration Steps 

1. Download the diffa-agent-X.war from the download site and copy it into the $JETTY_HOME/webapps directory.
2. Copy the diffa.properties file (you can find an example in the dist/src/main/assembly/resources/etc folder on Github) to an appropriate directory, e.g. $JETTY_HOME/etc
3. Using the following example, configure a jetty-diffa.xml file and place it into $JETTY_HOME/etc, making sure that the diffa.properties referenced in it refers to the same path as step (2):

{% include jetty-diffa.xml %}


4. In $JETTY_HOME/start.ini:
    * Add a line at the bottom to include etc/jetty-diffa.xml;
        * Set the plus and jndi options:
            
            OPTIONS=Server,jsp,jmx,resources,websocket,ext,plus,jndi
            
6. Add the following Apache database driver and pool libraries to the $JETTY_HOME/lib/ext directory:
    * derby-10.4.2.0.jar
    * commons-pool-1.3.jar
    * commons-dbcp-1.2.2.jar
    
7. Boot Jetty.


