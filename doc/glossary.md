---
layout: default
title: Glossary

---

{% include links.md %}

# Glossary Of Terms

#### Endpoint
A Diffa enabled participant that is can be addressed by a URL.

#### Participant
An application that understands the Diffa protocol and can respond to version digest requests.

##### Upstream Participant
A participant that constitutes the source of data for a pair of Diffa enabled applications.

##### Downstream Participant
A participant that constitutes the receiver of data for a pair of Diffa enabled applications.

#### Pair
A pair of [particpants][participant] that represents the flow of data between two different system. A pair consists of an [upstream][] participant and a [downstream][] participant.

#### Group
A user defined group of paired applications that represent a higher level business workflow.

### Entity

A generic term that represents the fundamental business objects that a participating application will either send (in the case of an [upstream][] participant) or receive (in the case of a [downstream][] participant).

### Data item

Commonly used synonym for [entity][].

### Version

An alphanumeric string that is unique to a given application entity and participant. A participant is required to consistently reproduce the same version string for an unchanged business entity. Note that a participant is free to produce a version string in any way that congruent with the particular applications notion of equality, as long as this is done in a consistent fashion. 

#### Digest

Represents the [version][] information for a particular data item. For an individual entity, the digest should be the version content and the date should be the timestamp of the entity. For an aggregate entity, the digest should be the hashed aggregate of the child entities within the given time range, and the date can be any representative time within the time period. The key is generally ignored in the case of aggregates, but it is suggested that a readable variant of the date is used to enhance understanding when reading digest lists. A version digest contains the following fields:

* the identifier of the entity
* the business date of the entity
* (otpionally) the a timestamp of when the particpant last updated the entity
* a digest string containing the [version][] of the [entity][]
