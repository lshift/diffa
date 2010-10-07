---
title: POST actions/:pairId/:actionId/:entityId | REST API Documentation
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
* [POST config/endpoints](/doc/rest/config/post/endpoints)
* [PUT config/endpoints/:id](/doc/rest/config/put/endpoints/p_id)
* [DELETE config/endpoints/:id](/doc/rest/config/delete/endpoints/p_id)
* [POST config/pairs](/doc/rest/config/post/pairs)
* [PUT config/pairs/:id](/doc/rest/config/put/pairs/p_id)
* [DELETE config/pairs/:id](/doc/rest/config/delete/pairs/p_id)
* [PUT config/groups/:id](/doc/rest/config/put/groups/p_id)
* [DELETE config/groups/:id](/doc/rest/config/delete/groups/p_id)
* [GET config/pairs/:id](/doc/rest/config/get/pairs/p_id)

### DifferencesResource

* [GET diffs/events/:sessionId/:evtSeqId](/doc/rest/diffs/get/events/p_sessionId/p_evtSeqId)
* [POST diffs/sessions](/doc/rest/diffs/post/sessions)
* [GET diffs/sessions/:sessionId](/doc/rest/diffs/get/sessions/p_sessionId)

### ActionsResource

* [GET actions/:pairId](/doc/rest/actions/get/p_pairId)
* POST actions/:pairId/:actionId/:entityId

### UsersResource

* [POST security/users](/doc/rest/security/post/users)
* [PUT security/users/:name](/doc/rest/security/put/users/p_name)
* [DELETE security/users/:name](/doc/rest/security/delete/users/p_name)
* [GET security/users](/doc/rest/security/get/users)
* [GET security/users/:name](/doc/rest/security/get/users/p_name)


</div>

<div id="resources" markdown="1">
POST actions/:pairId/:actionId/:entityId
=======================================================

<em>Invokes a named action on the pair and passes the id of the entity to which the action should be applied. </em>

Entity Type
-----------
ActionResult

URL
---
http://server:port/diffa-agent/rest/actions/:pairId/:actionId/:entityId

 
Mandatory Parameters
--------------------

### pairId

*string*

The indentifier of the pair

### actionId

*string*

The name of the action to invoke

### entityId

*string*

The id of the pair to perform the action on

Requires Authentication
-----------------------
no 

Example
-------
`` ``

JSON Schema
-----------
``{"type":"object","optional":true,"items":{"type":"string","optional":true},"properties":{"output":{"type":"string","optional":true},"result":{"type":"string","optional":true}}} ``
</div>