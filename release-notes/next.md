# Version 1.2.2 Change Log (yyyy-MM-dd)

## Release Overview

....

## New Features

* [31] - Added a settings page in the web UI to configure objects within a domain
* [35] - Added an API endpoint to retrieve the list of domains that a given user is a member of

## General Maintenance

* [28] - Addresses a issue whereby updating a user's config would regenerate their authentication token
* [29] - Introduces a simplification of the way pairs refer to endpoints
* [33] - Instead of letting the global error bar show a non-specific error, an error panel is rendered that shows the error returned by the server