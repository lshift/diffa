---
layout: default
title: Quick Start - Health Scanning

---

{% include links.md %}

# Integrating Developer's Guide To Health Scanning

This guide is designed to help you boot a working Diffa agent as quickly as possible, and to guide you through some hands-on examples of configuring and using Diffa in a health scanning scenario. In such a scenario, upstream and downstream systems are actively queried (scanned) by the Diffa agent.

## Download And Run Diffa

{% include download_and_run.md %}

## Configure Diffa

Diffa has a [REST interface][rest] for retrieving and modifying the configuration of a running Diffa agent.

You can view all of the current configuration by requesting an XML representation:

    $ curl http://localhost:7654/rest/config/xml
    <?xml version="1.0" encoding="UTF-8"?>
    <diffa-config/>

It is quite empty because this is a fresh, unconfigured Diffa agent. To make Diffa useful, we need to declare at least one [pair][pair] of [endpoints][endpoint].

Copy the following XML and save it as `diffa-config.xml`.

    <diffa-config>
      <!-- name: An identifier for the endpoint. We'll refer to this in the pair config.
           scan-url: The URL that Diffa should use to scan the application.
           content-type: The serialization format of the event messages. Currently only JSON has built-in support. -->
      <endpoint name="a" scan-url="http://localhost:3001/scan" content-type="application/json">
      </endpoint>
      <endpoint name="b" scan-url="http://localhost:3002/scan" content-type="application/json">
      </endpoint>
      <group name="my-group">
        <!-- key: An identifier for the pair.
             upstream: The endpoint that is the upstream system.
             downstream: The downstream endpoint.
             match-timeout: How long Diffa waits before showing a difference on the UI
             version-policy: "same" means both systems use the same digest scheme -->
        <pair key="my-pair" upstream="a" downstream="b" match-timeout="5" version-policy="same" />
      </group>  
    </diffa-config>

Apply the config by POSTing it to the REST interface:

    $ curl -X POST -d @diffa-config.xml http://localhost:7654/rest/config/xml -H "Content-type: application/xml"

## Building your sample applications

Given that every application deals with data in its own way, this guide isn't going to make any assumptions about how
your application chooses to do it. Instead, we're going to build a simple [Sinatra](http://sinatrarb.com) application in Ruby.

Assuming you have a functional Ruby environment, make sure you've got the Sinatra and JSON gems installed:

    gem install sinatra
    gem install json

Copy the following ruby code, and save it as `upstream.rb`.

    require 'rubygems'
    require 'sinatra'
    require 'json'
    
    get '/scan' do
      [
        {
          :attributes => [], 
          :metadata => {:id => 'id1', :lastUpdate => '2011-05-23T15:22:42.000+01:00', :vsn => 'x'}
        }
      ].to_json
    end

Run your upstream application by executing:

    ruby upstream.rb -p 3001
    
Write your downstream application similarly, by saving the following to `downstream.rb`.

    require 'rubygems'
    require 'sinatra'
    require 'json'
    
    get '/scan' do
      [
        {
          :attributes => [], 
          :metadata => {:id => 'id1', :lastUpdate => '2011-05-23T15:22:42.000+01:00', :vsn => 'y'}
        }
      ].to_json
    end

Run this downstream application by executing:

    ruby downstream.rb -p 3002

## Scanning for changes

In the Diffa UI, browse to the Admin page. Click the Scan button next to `my-pair`, and wait for the status to become
"Up to date". At this point, Diffa will have queried both your applications, and generated details on the differences
between your applications.

Return to the main page, and the Diffa UI should now be showing a difference type of "Data difference". This indicates
that Diffa has seen an entity with same ID in both applications, but they are reporting different versions.

### Repairing Data Differences and Re-scanning

Now that we've seen how Diffa detected the changes in our application, lets go back and correct it. Edit `downstream.rb`,
and change `:vsn => 'y'` to `:vsn => 'x'` (you should end up with an identical file content to `upstream.rb`). Kill and
restart the `downstream.rb`.

Note that since your application is not actively informing Diffa of changes, the UI will continue to show a difference. Return
to the Admin screen, and trigger another scan of the pair. Once the status has reached "Up to date", return to the main UI
and note that the difference has now disappeared.

