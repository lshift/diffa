# Version x.y Change Log (yyyy-MM-dd)

## Release Overview

...

## New Features

* [270] - Rolling windows for Endpoint Views to be used to filter scans based on an existing date or datetime attribute.

## General Maintenance

* [270] - Bug fix to Diffa as an adapter; presented users out of order.
* [275] - Migration process may identify wrong schema in Oracle.
* [279] - Run time dependency on Apache Zookeeper for multi-node identity provision service.
* [280] - Endpoints are represented by a surrogate key which will later be the mechanism for internally relating them to other entities.

## Library Upgrades

* Upgraded to JOOQ 2.6.0
* Upgraded to Hazelcast 2.4
* New Netflix Curator 1.2.3 dependency
* Upgraded from Google Collections (obsolete) to Guava 11.0.1

## Upgrading

Please note that Diffa will not automatically upgrade itself from any version below 1.7.
