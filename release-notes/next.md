 Version x.y Change Log (yyyy-MM-dd)

## Release Overview

...

## New Features

* [146] - Authentication for external scan participants can now be configured on a URL-specific basis
* [143] - Reworked inventory upload form to be pair-focused, along ith other UI/UX improvements.
* [142] - Inventory upload datetime constraints use a date picker for the date portion.
* [135] - Inventory upload form input fields are clearable with an "x".
* [129] - Improvements to the inventory submission form, including a JavaScript date picker for date range categories.
* [128] - Registered users and domains are now exposed via the Diffa scanning protocol so the agent can act as a participant
* [126] - Introduced a new REST API for performing arbitrary queries for time aggregated differences
* [111] - Support arbitrary limiting of operations in participant scanning, real-time event submission and inventory submission.
* [107] - Added the ability to initiate a scan from behind a firewall
* [84]  - If we've got at least two endpoints defined, then try to make selecting the endpoints in the settings page sensible
* [138] - Auto-focus on newly added constraint rows in the settings page 

## General Maintenance

* [109] - Log a summary of each Query triggered by a participant scan.
* [113] - Improve validation of pair definitions.
* [114] - Ensure that changes without attributes are processed.
* [118] - Make sure that ChangeEvents contain the mandatory fields
* [123] - Matched entities are now updated in caches immediately
* [124] - Replacement mechanism for caching results of aggregation.
* [127] - Support for infinite panning on the heat map
* [132] - Made sure that a change to a pair or endpoint config results in a change the Etag returned when querying for differences
* [134] - Reduce startup and pair registration time by using Apache httpclient instead of Jersey.

## Library Upgrades

* Upgraded to Lucene 3.6.0

## Upgrading

Diffa will automatically upgrade itself to this version from release 1.4 onwards.
