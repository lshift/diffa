---
title: REST API Documentation
layout: default
---

REST API Documentation
======================

<div id="collections" markdown="1">
ConfigurationResource
-----------

* [POST config/groups](config/post/groups)
* [GET config/groups/:id](config/get/groups/p_id)
* [GET config/endpoints](config/get/endpoints)
* [GET config/groups](config/get/groups)
* [GET config/endpoints/:id](config/get/endpoints/p_id)
* [DELETE config/endpoints/:id](config/delete/endpoints/p_id)
* [DELETE config/pairs/:id](config/delete/pairs/p_id)
* [DELETE config/groups/:id](config/delete/groups/p_id)
* [GET config/pairs/:id](config/get/pairs/p_id)
* [POST config/endpoints](config/post/endpoints)
* [PUT config/endpoints/:id](config/put/endpoints/p_id)
* [POST config/pairs](config/post/pairs)
* [PUT config/pairs/:id](config/put/pairs/p_id)
* [PUT config/groups/:id](config/put/groups/p_id)

DifferencesResource
-----------

* [GET diffs/events/:sessionId/:evtSeqId/:participant](diffs/get/events/p_sessionId/p_evtSeqId/p_participant)
* [POST diffs/sessions](diffs/post/sessions)
* [GET diffs/sessions/:sessionId](diffs/get/sessions/p_sessionId)

ActionsResource
-----------

* [GET actions/:pairId](actions/get/p_pairId)
* [POST actions/:pairId/:actionId/:entityId](actions/post/p_pairId/p_actionId/p_entityId)

UsersResource
-----------

* [DELETE security/users/:name](security/delete/users/p_name)
* [GET security/users](security/get/users)
* [GET security/users/:name](security/get/users/p_name)
* [POST security/users](security/post/users)
* [PUT security/users/:name](security/put/users/p_name)


</div>