 Version x.y Change Log (yyyy-MM-dd)

## Release Overview

...

## New Features

* [126] - Introduced a new REST API for performing arbitrary queries for time aggegrated differences
* [111] - Support arbitrary limiting of operations in participant scanning, real-time event submission and inventory submission.
* [107] - Added the ability to initiate a scan from behind a firewall
* [84]  - If we've got at least two endpoints defined, then try to make selecting the endpoints in the settings page sensible

## General Maintenance

* [109] - Log a summary of each Query triggered by a participant scan.
* [113] - Improve validation of pair definitions.
* [114] - Ensure that changes without attributes are processed.
* [118] - Make sure that ChangeEvents contain the mandatory fields
* [123] - Matched entities are now updated in caches immediately
* [124] - Replacement mechanism for caching results of aggregation.

## Library Upgrades

* Upgraded to ...

## Upgrading

Diffa will automatically upgrade itself to this version from release 1.4 onwards.
