# Version x.y Change Log (yyyy-MM-dd)

## Release Overview

...

## New Features

* [255] - Introduce sub spaces.
* [267] - Support for disabling validation of Entity ordering.

## General Maintenance

* [254] - The database is now keyed on spaces with a surrogate key.
* [260] - Introduce extents as a way to asynchronously vacuum the DIFFS table.
* [266] - Fixed a caching bug in the space path lookup
* [268] - Fixed a special case bug in the space path lookup for the root space.
* [269] - Forward ported a fix for a memory leak from the 1.5.6 release tag.

## Library Upgrades

* Upgraded to JOOQ 2.5.0
* Upgraded to Hazelcast 2.2
* Upgraded to Lucene 3.6.1
* Upgraded to Spring Security 3.1.2
* Downgraded to HSQLDB 2.2.8 (only relevant for testing purposes)

## Upgrading

Please note that Diffa will not automatically upgrade itself to this version from release 1.4.
