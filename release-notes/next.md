# Version x.y Change Log (yyyy-MM-dd)

## Release Overview

...

## New Features

* [76]  - Protection against endpoint category changes that would cause the stored data to become invalid
* [99]  - Made the correlation writer proxy timeout a domain configuration option

## Deprecated Features

* [96]  - Removed the REST API call to trigger a scan for all pairs within a domain

## General Maintenance

* [92]  - Increased the minimum blob size in the heatmap and scaled it logarithmically
* [98]  - Addressed a match error in a receive loop of the pair actor that results in a spurious log entry
* [100] - Reduced the verbosity of connection refused errors in the logs
* [103] - Notify the commencement of a scan to the pair activity log

## Library Upgrades

* Upgraded to Scala 2.9.1-1

## Upgrading

Diffa will automatically upgrade itself to this version from release 1.4 onwards.