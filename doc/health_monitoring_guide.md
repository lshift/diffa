---
layout: default
title: Quick Start Guide

---

{% include links.md %}

# Integrating Developer's Guide To Health Monitoring

This guide is designed to help you boot a working Diffa agent as quickly as possible, and to guide you through some hands-on examples of configuring and using Diffa in a health monitoring scenario. In such a scenario, upstream and downstream systems send [change events][event] to the Diffa agent.

## Download And Run Diffa

Get the latest Standalone Diffa agent package from the Diffa [download page][download] on GitHub.

    $ wget https://github.com/downloads/lshift/diffa/diffa-0.9.3-SNAPSHOT.zip

Extract the contents of the archive, and boot the agent:

    $ unzip diffa-0.9.3-SNAPSHOT.zip -d diffa-agent
    $ cd diffa-agent/bin
    $ sh agent.sh

Once you see the log message `Diffa Agent successfully initialized`, open a browser window and go to [http://localhost:7654/](http://localhost:7654/). You should see the Diffa UI.

## Configure Diffa

Diffa has a [REST interface][rest] for retrieving and modifying the configuration of a running Diffa agent.

You can view all of the current configuration by requesting an XML representation:

    $ curl http://localhost:7654/rest/config/xml
    <?xml version="1.0" encoding="UTF-8"?>
    <diffa-config/>

It is quite empty because this is a fresh, unconfigured Diffa agent. To make Diffa useful, we need to declare at least one [pair][] of [endpoints][endpoint].

Copy the following XML and save it as `diffa-config.xml`.

    <diffa-config>
      <!-- name: An identifier for the endpoint. We'll refer to this in the pair config.
           url: This is required, but not relevant for now. It's used for scanning scenarios.
           content-type: The serialization format of the event messages. Currently only JSON has built-in support. -->
      <endpoint name="a" url="http://example.com/a" content-type="application/json">
      </endpoint>
      <endpoint name="b" url="http://example.com/b" content-type="application/json">
      </endpoint>
      <group name="my-group">
        <!-- key: An identifier for the pair.
             upstream: The endpoint that is the upstream system.
             downstream: The downstream endpoint.
             matching-timeout: How long Diffa waits before showing a difference on the UI
             version-policy: "same" means both systems use the same digest scheme -->
        <pair key="my-pair" upstream="a" downstream="b" matching-timeout="5" version-policy="same" />
      </group>  
    </diffa-config>

Apply the config by POSTing it to the REST interface:

    $ curl -X POST -d @diffa-config.xml http://localhost:7654/rest/config/xml -H "Content-type: application/xml"

## Notify Diffa Of Events

Let's test-drive the configured Diffa agent by simulating some of the [events][event] that monitored applications might send to the agent in a real-world scenario.

### Upstream Change Event

One scenario might be that a new [entity][] appears in the upstream system, but not yet in the downstream. To simulate the upstream system sending a change event to Diffa, we can send a JSON message directly to the agent. Save the following JSON as `upstream.json`:

    {"attributes":[],"eventType":"upstream","metadata":{"id":"id1","endpoint":"a",
    "lastUpdate":"2011-05-23T15:22:42.000+01:00","vsn":"x"}}
    
> A brief explanation of the keys in this JSON:
> * `attributes` is not used for this health-monitoring scenario
> * `eventType` is one of "upstream", "downstream-same", "downstream-correlated"
> * `metadata` holds information about the event, including;
> * `id`, the unique identifier of the entity
> * `endpoint`, the endpoint from which the event originated
> * `lastUpdate`, the last time at which the entity changed
> * `vsn`, the [digest][] of the entity

POST it to the agent:

    $ curl -X POST -d @upstream.json http://localhost:7654/changes -H "Content-type: application/json"
    
On the Diffa UI, you should see a dot appear on the heatmap. On the vertical axis, it aligns with `my-pair`; that's the name of the pair you configured. On the horizontal axis, it aligns with the `lastUpdate` value of the event which was sent. You should see a difference with the type "Missing from downstream" in the table.
    
### Downstream Change Event

Continuing the scenario, we'll make a different version of the same entity appear in the downstream system. POST the following downstream event to the agent:

    {"attributes":[],"eventType":"downstream-same","metadata":{"id":"id1","endpoint":"b",
    "lastUpdate":"2011-05-23T15:22:42.000+01:00","vsn":"y"}}

Note that, in the `metadata`, the `id` is the same: this event relates to the same entity which appeared on the upstream system. But notice that `vsn` is different: the two systems hold different versions of the same entity.

The Diffa UI should now be showing a difference type of "Data difference".

### Repairing Data Differences

As the last step in our scenario, we'll pretend that some remedial action has been taken to repair the data difference. (That could simply be that the upstream system re-sends the entity to the downstream system, an action that can be performed from the Diffa UI. *TODO: docs on how to configure repair actions*)

This would cause the downstream system to send another change event to the agent. To simulate this, POST the same downstream event again, but with the `vsn` changed to `"x"`:

    {"attributes":[],"eventType":"downstream-same","metadata":{"id":"id1","endpoint":"b",
    "lastUpdate":"2011-05-23T15:22:42.000+01:00","vsn":"x"}}

The difference should disappear from the Diffa UI.

