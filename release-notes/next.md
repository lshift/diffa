# Version x.y Change Log (yyyy-MM-dd)

## Release Overview

...

## New Features

* [226] - The API namespace /domains is deprecated in favor of /spaces. Use server side forwarding to provide short term backwards compatibility.
* [219] - The collation of entity and aggregate scan results are validated as part of the scan deserialization pipeline
* [227] - The collation that Diffa uses itself to respond to scan requests is now configurable

## General Maintenance

* [228] - The underlying client used by the Scan Participant REST client was inappropriately re-used, causing all but the first scan to fail.
* [229] - Addressed a regression whereby ignoring a difference in the UI did not have immediate effect.

## Library Upgrades

* Upgraded to ...

## Upgrading

Diffa will automatically upgrade itself to this version from release 1.4 onwards.
