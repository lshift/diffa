# Version x.y Change Log (yyyy-MM-dd)

## Release Overview

...

## New Features

* [40] - Domain-scope API endpoints are now prefixed with "/domains", e.g., /diffa becomes /domains/diffa
* [41] - Introduce a status page at /status to allow easy monitoring of agent status
* [42] - The /rest prefix has been removed, e.g. /rest/domains/abc becomes /domains/abc.
* [45] - Return a 404 response when a superuser attempts to access resources under non-existent domains
* [53] - Disable all diagnostic explanations by default, but support enabling explanation limits configured by pair and domain.

## General Maintenance

* [54] - Corrected a behavior whereby when a correlation that previous had upstream and downstream elements has one of the elements removed, the correlation would get (incorrectly) marked as matched.

## Library Upgrades

* Upgraded to akka 1.3.1
* Upgraded to slf4j 1.6.4
* Upgraded to Logback 1.0.0
