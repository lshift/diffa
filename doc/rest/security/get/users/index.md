---
title: GET security/users | REST API Documentation
layout: default
---

<div id="menu" markdown="1">
Contents
--------

### ConfigurationResource

* [POST config/groups](/doc/rest/config/post/groups)
* [GET config/groups/:id](/doc/rest/config/get/groups/p_id)
* [GET config/xml](/doc/rest/config/get/xml)
* [POST config/xml](/doc/rest/config/post/xml)
* [GET config/endpoints](/doc/rest/config/get/endpoints)
* [GET config/groups](/doc/rest/config/get/groups)
* [GET config/repair-actions](/doc/rest/config/get/repair-actions)
* [GET config/endpoints/:id](/doc/rest/config/get/endpoints/p_id)
* [POST config/endpoints](/doc/rest/config/post/endpoints)
* [PUT config/endpoints/:id](/doc/rest/config/put/endpoints/p_id)
* [DELETE config/endpoints/:id](/doc/rest/config/delete/endpoints/p_id)
* [POST config/pairs](/doc/rest/config/post/pairs)
* [PUT config/pairs/:id](/doc/rest/config/put/pairs/p_id)
* [DELETE config/pairs/:id](/doc/rest/config/delete/pairs/p_id)
* [GET config/pairs/:id/repair-actions](/doc/rest/config/get/pairs/p_id/repair-actions)
* [POST config/pairs/:id/repair-actions](/doc/rest/config/post/pairs/p_id/repair-actions)
* [DELETE config/pairs/:pairKey/repair-actions/:name](/doc/rest/config/delete/pairs/p_pairKey/repair-actions/p_name)
* [POST config/pairs/:id/escalations](/doc/rest/config/post/pairs/p_id/escalations)
* [DELETE config/pairs/:pairKey/escalations/:name](/doc/rest/config/delete/pairs/p_pairKey/escalations/p_name)
* [PUT config/groups/:id](/doc/rest/config/put/groups/p_id)
* [DELETE config/groups/:id](/doc/rest/config/delete/groups/p_id)
* [GET config/pairs/:id](/doc/rest/config/get/pairs/p_id)

### DifferencesResource

* [GET diffs/events/:sessionId/:evtSeqId/:participant](/doc/rest/diffs/get/events/p_sessionId/p_evtSeqId/p_participant)
* [POST diffs/sessions](/doc/rest/diffs/post/sessions)
* [POST diffs/sessions/:sessionId/sync](/doc/rest/diffs/post/sessions/p_sessionId/sync)
* [POST diffs/sessions/scan_all](/doc/rest/diffs/post/sessions/scan_all)
* [GET diffs/sessions/all_scan_states](/doc/rest/diffs/get/sessions/all_scan_states)
* [GET diffs/sessions/:sessionId/sync](/doc/rest/diffs/get/sessions/p_sessionId/sync)
* [GET diffs/sessions/:sessionId](/doc/rest/diffs/get/sessions/p_sessionId)
* [GET diffs/sessions/:sessionId/page](/doc/rest/diffs/get/sessions/p_sessionId/page)
* [GET diffs/sessions/:sessionId/zoom](/doc/rest/diffs/get/sessions/p_sessionId/zoom)

### ActionsResource

* [GET actions/:pairId](/doc/rest/actions/get/p_pairId)
* [POST actions/:pairId/:actionId](/doc/rest/actions/post/p_pairId/p_actionId)
* [POST actions/:pairId/:actionId/:entityId](/doc/rest/actions/post/p_pairId/p_actionId/p_entityId)

### UsersResource

* [POST security/users](/doc/rest/security/post/users)
* [PUT security/users/:name](/doc/rest/security/put/users/p_name)
* [DELETE security/users/:name](/doc/rest/security/delete/users/p_name)
* GET security/users
* [GET security/users/:name](/doc/rest/security/get/users/p_name)

### EscalationsResource

* [GET escalations/:pairId](/doc/rest/escalations/get/p_pairId)

### ScanningResource

* [DELETE scanning/pairs/:pairKey/scan](/doc/rest/scanning/delete/pairs/p_pairKey/scan)


</div>

<div id="resources" markdown="1">
GET security/users
=======================================================

<em>Returns a list of all the users registered with the agent.</em>

Entity Type
-----------
Array of User

URL
---
http://server:port/diffa-agent/rest/security/users

Example
-------</div>
<div id="example">
<pre class="brush: js">[ {
  "name" : "joe.public",
  "email" : "joe.public@acme.com"
} ]</pre>
</div>