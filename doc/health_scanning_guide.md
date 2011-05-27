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
your application chooses to do it. Instead, we're going to build simple [Sinatra](http://sinatrarb.com) applications in Ruby
with hardcoded data sets.

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

## Repairing Data Differences and Re-scanning

Now that we've seen how Diffa detected the changes in our application, lets go back and correct it. Edit `downstream.rb`,
and change `:vsn => 'y'` to `:vsn => 'x'` (you should end up with an identical file content to `upstream.rb`). Kill and
restart the `downstream.rb`.

Note that since your application is not actively informing Diffa of changes, the UI will continue to show a difference. Return
to the Admin screen, and trigger another scan of the pair. Once the status has reached "Up to date", return to the main UI
and note that the difference has now disappeared.

## Optimising Communication

As the amount of data within your application grows, the size of the response to the scan query will grow too. For applications
that handle very little data, then this may never be a problem. However most applications, of the course of the life, tend
to accumulate more and more data. Returning all the data to Diffa each time could result in substantial network traffic, and potentially
degradation to application performance due to the need to serialise so much data into a single response.

To deal with this, Diffa provides a mechanism for endpoints to communicate with the agent via digests. A digest is an aggregation of a
series of versions, allowing for Diffa and the endpoint to communicate about an entire range of data via a single result. For example,
consider an application with a dataset like the following:

<table>
  <tr><td>ID</td><td>Version</td><td>Metadata</td></tr>
  <tr><td>id1</td><td>v1</td><td>{'published' = '2011-05-27'}</td></tr>
  <tr><td>id2</td><td>v2</td><td>{'published' = '2011-05-26'}</td></tr>
  <tr><td>id3</td><td>v3</td><td>{'published' = '2011-05-27'}</td></tr>
</table>

To reduce communication, the agent and the endpoint could communicate about the aggregation versions of entities that were 
published on each day. This would reduce the initial communication to:

<table>
  <tr><td>Aggregate ID</td><td>Aggregate Version</td><td>Metadata</td></tr>
  <tr><td>2011-05-26</td><td>Aggregate(v1,v3)</td><td>{'published' = '2011-05-26'}</td></tr>
  <tr><td>2011-05-27</td><td>Aggregate(v2)</td><td>{'published' = '2011-05-27'}</td></tr>
</table>

Of course, in the scenario where we only have three elements, this doesn't seem like a huge saving. But the principal can
be applied in scenarios where there are far more, and thus result in substantial savings.

In order to allow Diffa to communicate like this, a number of things need to be done:

* Configure Diffa to categorise data
* Support category requests to your application's scanning endpoint

## Configuring Diffa to categorise data

First up, we'll reconfigure Diffa. Save the following (updated) XML.

    <diffa-config>
      <endpoint name="a" scan-url="http://localhost:3001/scan" content-type="application/json">
        <!-- A range-category indicates that data can be categorised within value ranges. 
          name: the name of the field that your application will refer to this information as in metadata
          data-type: the type of the data in the field, used to determine the appropriate mechanism for exploring the category.
        -->
        <range-category name="published" data-type="date" />
      </endpoint>
      <endpoint name="b" scan-url="http://localhost:3002/scan" content-type="application/json" />
      <group name="my-group">
        <pair key="my-pair" upstream="a" downstream="b" match-timeout="5" version-policy="same" />
      </group>  
    </diffa-config>

Apply the config by POSTing it to the REST interface:

    $ curl -X POST -d @diffa-config.xml http://localhost:7654/rest/config/xml -H "Content-type: application/xml"

Diffa is now ready to categorise data coming from your application, and submit more optimal aggregate requests.

## Support Category Requests to your application

When Diffa queries an application that supports categories, it will perform the scan in a series of operations. Initially,
it will scan with each category at the least granular level, that is, the view where it can see the most data for the smallest
amount of returned information. For any returned digest that does not match it's expected view, it will refine the query to retrieve
data constrained by the given category.

Previously, Diffa was querying your application for data by performing a `GET` request on `/scan` with no query parameters. In our
new scenario, Diffa will make an initial `GET` request to `/scan?published-granularity=yearly`. It will expect the application to
return a series of digest with the data aggregated by year. For example,

    [{"attributes":["2010"], "metadata":{"vsn":"abc"}}, {"attributes":["2011"], "metadata":"def"}]
    
If Diffa's internal view of your application's data does not match this aggregate view for a category, a more refined request will be
submitted, such as a `GET` to `/scan?published-granularity=monthly&published-start=2011-01-01&published-end=2011-12-31`.

To provide a simple implementation of this in our application, replace `upstream.rb` with:

    require 'rubygems'
    require 'sinatra'
    require 'json'
    require 'time'
    require 'md5'
    
    DATA = [
      {:attributes => ['2011-05-26'],  :metadata => {:id => 'id1', :lastUpdate => Time.now.iso8601, :vsn => 'y'}},
      {:attributes => ['2011-05-27'],  :metadata => {:id => 'id2', :lastUpdate => Time.now.iso8601, :vsn => 'y'}},
      {:attributes => ['2011-05-26'],  :metadata => {:id => 'id3', :lastUpdate => Time.now.iso8601, :vsn => 'y'}},
    ]
    
    get '/scan' do
      filtered = filter_data(DATA, params['published-start'], params['published-end'])
    
      if params['published-granularity']
        aggregate_by_published(filtered, params['published-granularity']).to_json
      else
        filtered.to_json
      end
    end
    
    def filter_data(data, pub_start, pub_end)
      data.select do |d| 
        # Filter each row of the data. For the data to be included, the bound needs to be either null or not
        # exceeded.
        published = Time.parse(d[:attributes][0])
      
        (pub_start.nil? || published >= Time.parse(pub_start)) && 
          (pub_end.nil? || published <= Time.parse(pub_end))
      end
    end
    
    def aggregate_by_published(data, granularity)
      # Take each piece of data, and then based on the granularity, put it into a "bucket" for the given
      # category.
      bucketed = {}
      data.each do |d|
        published = d[:attributes][0]
        bucket_name = case granularity
          when "yearly" then published[0..3]
          when "monthly" then published[0..6]
          when "daily" then published
        end
        
        # Add the data element to the named bucket
        (bucketed[bucket_name] ||= []) << d
      end
      
      # Work through each bucket, and generate an aggregate version
      bucketed.map do |name, entries|
        combined_version = MD5.hexdigest(entries.map { |e| e[:metadata][:vsn] }.join(''))
      
        {:attributes => [name], :metadata => {:vsn => combined_version}}
      end
    end
