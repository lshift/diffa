---
title: GET :domain/diffs/zoom | REST API Documentation
layout: default
---

<div id="menu" markdown="1">
Contents
--------

### ChangesResource

* [POST :domain/changes/:endpoint](/doc/rest/p_domain/changes/post/p_endpoint)

### ConfigurationResource

* [GET :domain/config/pairs/:id](/doc/rest/p_domain/config/get/pairs/p_id)
* [POST :domain/config/xml](/doc/rest/p_domain/config/post/xml)
* [GET :domain/config/members](/doc/rest/p_domain/config/get/members)
* [GET :domain/config/repair-actions](/doc/rest/p_domain/config/get/repair-actions)
* [GET :domain/config/endpoints](/doc/rest/p_domain/config/get/endpoints)
* [GET :domain/config/xml](/doc/rest/p_domain/config/get/xml)
* [DELETE :domain/config/endpoints/:id](/doc/rest/p_domain/config/delete/endpoints/p_id)
* [DELETE :domain/config/pairs/:id](/doc/rest/p_domain/config/delete/pairs/p_id)
* [DELETE :domain/config/pairs/:pairKey/repair-actions/:name](/doc/rest/p_domain/config/delete/pairs/p_pairKey/repair-actions/p_name)
* [GET :domain/config/pairs/:id/repair-actions](/doc/rest/p_domain/config/get/pairs/p_id/repair-actions)
* [DELETE :domain/config/pairs/:pairKey/escalations/:name](/doc/rest/p_domain/config/delete/pairs/p_pairKey/escalations/p_name)
* [POST :domain/config/members/:username](/doc/rest/p_domain/config/post/members/p_username)
* [DELETE :domain/config/members/:username](/doc/rest/p_domain/config/delete/members/p_username)
* [GET :domain/config/endpoints/:id](/doc/rest/p_domain/config/get/endpoints/p_id)
* [POST :domain/config/endpoints](/doc/rest/p_domain/config/post/endpoints)
* [PUT :domain/config/endpoints/:id](/doc/rest/p_domain/config/put/endpoints/p_id)
* [POST :domain/config/pairs](/doc/rest/p_domain/config/post/pairs)
* [PUT :domain/config/pairs/:id](/doc/rest/p_domain/config/put/pairs/p_id)
* [POST :domain/config/pairs/:id/repair-actions](/doc/rest/p_domain/config/post/pairs/p_id/repair-actions)
* [POST :domain/config/pairs/:id/escalations](/doc/rest/p_domain/config/post/pairs/p_id/escalations)

### ScanningResource

* [GET :domain/scanning/states](/doc/rest/p_domain/scanning/get/states)
* [POST :domain/scanning/pairs/:pairKey/scan](/doc/rest/p_domain/scanning/post/pairs/p_pairKey/scan)
* [POST :domain/scanning/scan_all](/doc/rest/p_domain/scanning/post/scan_all)
* [DELETE :domain/scanning/pairs/:pairKey/scan](/doc/rest/p_domain/scanning/delete/pairs/p_pairKey/scan)

### DiagnosticsResource

* [GET :domain/diagnostics/:pairKey/log](/doc/rest/p_domain/diagnostics/get/p_pairKey/log)

### DifferencesResource

* [GET :domain/diffs/events/:evtSeqId/:participant](/doc/rest/p_domain/diffs/get/events/p_evtSeqId/p_participant)
* GET :domain/diffs/zoom

### ActionsResource

* [GET :domain/actions/:pairId](/doc/rest/p_domain/actions/get/p_pairId)
* [POST :domain/actions/:pairId/:actionId](/doc/rest/p_domain/actions/post/p_pairId/p_actionId)
* [POST :domain/actions/:pairId/:actionId/:entityId](/doc/rest/p_domain/actions/post/p_pairId/p_actionId/p_entityId)

### UsersResource

* [GET security/users](/doc/rest/security/get/users)
* [GET security/users/:name](/doc/rest/security/get/users/p_name)
* [DELETE security/users/:name](/doc/rest/security/delete/users/p_name)
* [POST security/users](/doc/rest/security/post/users)
* [PUT security/users/:name](/doc/rest/security/put/users/p_name)

### EscalationsResource

* [GET :domain/escalations/:pairId](/doc/rest/p_domain/escalations/get/p_pairId)

### SystemConfigResource

* [DELETE root/domains/:name](/doc/rest/root/delete/p_domains/p_name)
* [POST root/domains](/doc/rest/root/post/p_domains)


</div>

<div id="resources" markdown="1">
GET :domain/diffs/zoom
=======================================================

<em>Returns a zoomed view of the data within a specific time range</em>

Entity Type
-----------
Response

URL
---
http://server:port/diffa-agent/rest/:domain/diffs/zoom

 
Mandatory Parameters
--------------------

### range-start

*date*

The starting time for any differences

### range-end

*date*

The ending time for any differences

### bucketing

*int*

The size in elements in the zoomed view

</div>
