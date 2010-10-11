---
title: GET diffs/events/:sessionId/:evtSeqId/:participant | REST API Documentation
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
* [PUT config/pairs/:id](/doc/rest/config/put/pairs/p_id)
* [PUT config/groups/:id](/doc/rest/config/put/groups/p_id)

### DifferencesResource

* GET diffs/events/:sessionId/:evtSeqId/:participant
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
GET diffs/events/:sessionId/:evtSeqId/:participant
=======================================================

<em>Returns the verbatim detail from each participant for the event that corresponds to the sequence id.</em>

Entity Type
-----------
String

URL
---
http://server:port/diffa-agent/rest/diffs/events/:sessionId/:evtSeqId/:participant

 
Mandatory Parameters
--------------------

### sessionId

*string*

Session ID

### evtSeqId

*string*

Event Sequence ID

### participant

*string*

Denotes whether the upstream or downstream participant is intended. Legal values are {upstream,downstream}.

Requires Authentication
-----------------------
no 

Example
-------
`` ``

JSON Schema
-----------
``{"type":"string","optional":true} ``
</div>