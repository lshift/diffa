---
title: PUT config/pairs/:id | REST API Documentation
layout: default
---

<div id="menu" markdown="1">
Contents
--------

### ConfigurationResource

* [POST config/groups](/doc/rest/config/post/groups)
* [GET config/groups/:id](/doc/rest/config/get/groups/p_id)
* [GET config/endpoints](/doc/rest/config/get/endpoints)
* [GET config/groups](/doc/rest/config/get/groups)
* [GET config/endpoints/:id](/doc/rest/config/get/endpoints/p_id)
* [DELETE config/endpoints/:id](/doc/rest/config/delete/endpoints/p_id)
* [DELETE config/pairs/:id](/doc/rest/config/delete/pairs/p_id)
* [DELETE config/groups/:id](/doc/rest/config/delete/groups/p_id)
* [GET config/pairs/:id](/doc/rest/config/get/pairs/p_id)
* [POST config/endpoints](/doc/rest/config/post/endpoints)
* [PUT config/endpoints/:id](/doc/rest/config/put/endpoints/p_id)
* [POST config/pairs](/doc/rest/config/post/pairs)
* PUT config/pairs/:id
* [PUT config/groups/:id](/doc/rest/config/put/groups/p_id)

### DifferencesResource

* [GET diffs/events/:sessionId/:evtSeqId/:participant](/doc/rest/diffs/get/events/p_sessionId/p_evtSeqId/p_participant)
* [POST diffs/sessions](/doc/rest/diffs/post/sessions)
* [GET diffs/sessions/:sessionId](/doc/rest/diffs/get/sessions/p_sessionId)

### ActionsResource

* [GET actions/:pairId](/doc/rest/actions/get/p_pairId)
* [POST actions/:pairId/:actionId/:entityId](/doc/rest/actions/post/p_pairId/p_actionId/p_entityId)

### UsersResource

* [DELETE security/users/:name](/doc/rest/security/delete/users/p_name)
* [GET security/users](/doc/rest/security/get/users)
* [GET security/users/:name](/doc/rest/security/get/users/p_name)
* [POST security/users](/doc/rest/security/post/users)
* [PUT security/users/:name](/doc/rest/security/put/users/p_name)


</div>

<div id="resources" markdown="1">
PUT config/pairs/:id
=======================================================

<em>Updates the attributes of a pairing between two endpoints that are already registered with the agent.</em>

Entity Type
-----------
PairDef

URL
---
http://server:port/diffa-agent/rest/config/pairs/:id

 
Mandatory Parameters
--------------------

### id

*string*

Pair ID

Requires Authentication
-----------------------
no 

Example
-------
``{"matchingTimeout":120,"versionPolicyName":"versionPolicyName","groupKey":"groupKey","downstreamName":"downstreamName","upstreamName":"upstreamName","pairKey":"pairKey"} ``

JSON Schema
-----------
``{"type":"object","optional":true,"items":{"type":"string","optional":true},"properties":{"matchingTimeout":{"type":"integer","optional":true},"versionPolicyName":{"type":"string","optional":true},"groupKey":{"type":"string","optional":true},"downstreamName":{"type":"string","optional":true},"upstreamName":{"type":"string","optional":true},"pairKey":{"type":"string","optional":true}}} ``
</div>