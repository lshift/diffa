---
layout: default
title: Integration Guide

---

{% include links.md %}

# Integration Guide

This guide is designed to help developers add Diffa support to their application.

### Protocol

The Diffa agent makes callback queries to Diffa [participants][participant]. This means that a participant needs to be able to respond to the following calls:  

* A query for the version digests that the participant knows about. The granularity of the query response can be specified by the requester and can be one of the following granularities: Individual, Daily,Monthly,Yearly. The response is a list of version digest messages: key, date, digest

* An upstream participant will be queried to return the content corresponding to an identifier
* A downstream participant will be queried to return the version of an entity body

### Transport

The Diffa protocol currently uses HTTP as a transport. Messages are encoded with JSON. 