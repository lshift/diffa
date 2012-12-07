# Version x.y Change Log (yyyy-MM-dd)

## Release Overview

...

## New Features

* [255] - Introduce sub spaces.
* [267] - Support for disabling validation of Entity ordering.
* [270] - Rolling windows for Endpoint Views to be used to filter scans based on an existing date or datetime attribute.

## General Maintenance

* [254] - The database is now keyed on spaces with a surrogate key.
* [260] - Introduce extents as a way to asynchronously vacuum the DIFFS table.
* [266] - Fixed a caching bug in the space path lookup
* [270] - Bug fix to Diffa as an adapter; presented users out of order.
* [275] - Migration process may identify wrong schema in Oracle.
* [279] - Run time dependency on Apache Zookeeper for multi-node identity provision service.

## Library Upgrades

* Upgraded to JOOQ 2.5.0
* Upgraded to Hazelcast 2.2
* Upgraded to Lucene 3.6.1
* Upgraded to Spring Security 3.1.2
* Downgraded to HSQLDB 2.2.8 (only relevant for testing purposes)
* New Netflix Curator 1.2.3 dependency
* Upgraded from Google Collections (obsolete) to Guava 11.0.1

## Upgrading

Please note that Diffa will not automatically upgrade itself to this version from release 1.4.
