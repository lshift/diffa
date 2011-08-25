---
title: REST API Documentation
layout: default
---

REST API Documentation
======================

<div id="collections" markdown="1">
ChangesResource
-----------

* [POST :domain/changes/:endpoint](p_domain/changes/post/p_endpoint)

ConfigurationResource
-----------

* [GET :domain/config/pairs/:id](p_domain/config/get/pairs/p_id)
* [POST :domain/config/xml](p_domain/config/post/xml)
* [GET :domain/config/members](p_domain/config/get/members)
* [GET :domain/config/repair-actions](p_domain/config/get/repair-actions)
* [GET :domain/config/endpoints](p_domain/config/get/endpoints)
* [GET :domain/config/xml](p_domain/config/get/xml)
* [DELETE :domain/config/endpoints/:id](p_domain/config/delete/endpoints/p_id)
* [DELETE :domain/config/pairs/:id](p_domain/config/delete/pairs/p_id)
* [DELETE :domain/config/pairs/:pairKey/repair-actions/:name](p_domain/config/delete/pairs/p_pairKey/repair-actions/p_name)
* [GET :domain/config/pairs/:id/repair-actions](p_domain/config/get/pairs/p_id/repair-actions)
* [DELETE :domain/config/pairs/:pairKey/escalations/:name](p_domain/config/delete/pairs/p_pairKey/escalations/p_name)
* [POST :domain/config/members/:username](p_domain/config/post/members/p_username)
* [DELETE :domain/config/members/:username](p_domain/config/delete/members/p_username)
* [GET :domain/config/endpoints/:id](p_domain/config/get/endpoints/p_id)
* [POST :domain/config/endpoints](p_domain/config/post/endpoints)
* [PUT :domain/config/endpoints/:id](p_domain/config/put/endpoints/p_id)
* [POST :domain/config/pairs](p_domain/config/post/pairs)
* [PUT :domain/config/pairs/:id](p_domain/config/put/pairs/p_id)
* [POST :domain/config/pairs/:id/repair-actions](p_domain/config/post/pairs/p_id/repair-actions)
* [POST :domain/config/pairs/:id/escalations](p_domain/config/post/pairs/p_id/escalations)

ScanningResource
-----------

* [GET :domain/scanning/states](p_domain/scanning/get/states)
* [POST :domain/scanning/pairs/:pairKey/scan](p_domain/scanning/post/pairs/p_pairKey/scan)
* [POST :domain/scanning/scan_all](p_domain/scanning/post/scan_all)
* [DELETE :domain/scanning/pairs/:pairKey/scan](p_domain/scanning/delete/pairs/p_pairKey/scan)

DiagnosticsResource
-----------

* [GET :domain/diagnostics/:pairKey/log](p_domain/diagnostics/get/p_pairKey/log)

DifferencesResource
-----------

* [GET :domain/diffs/events/:evtSeqId/:participant](p_domain/diffs/get/events/p_evtSeqId/p_participant)
* [GET :domain/diffs/zoom](p_domain/diffs/get/zoom)

ActionsResource
-----------

* [GET :domain/actions/:pairId](p_domain/actions/get/p_pairId)
* [POST :domain/actions/:pairId/:actionId](p_domain/actions/post/p_pairId/p_actionId)
* [POST :domain/actions/:pairId/:actionId/:entityId](p_domain/actions/post/p_pairId/p_actionId/p_entityId)

UsersResource
-----------

* [GET security/users](security/get/users)
* [GET security/users/:name](security/get/users/p_name)
* [DELETE security/users/:name](security/delete/users/p_name)
* [POST security/users](security/post/users)
* [PUT security/users/:name](security/put/users/p_name)

EscalationsResource
-----------

* [GET :domain/escalations/:pairId](p_domain/escalations/get/p_pairId)

SystemConfigResource
-----------

* [DELETE root/domains/:name](root/delete/p_domains/p_name)
* [POST root/domains](root/post/p_domains)


</div>