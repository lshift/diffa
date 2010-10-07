---
layout: default
title: How It Works

---

{% include links.md %}

# How It Works

Diffa consists of at least four parts:

* The agent
* An upstream and a downstream participant
* The analysis UI and a CLI to configure and monitor a pairing

It looks like this:

![generic]

### Design

The idea behind Diffa was to implement a comparison tool that can detect conflicting differences between two independent systems. The  To decouple the comparison engine from the specifics of the applications under comparison, a query protocol ash been devised. Using this protocol, the agent can introspect the body of entities in a [participant][] whilst still being able to treat the content of the [upstream][] and [downstream][] systems as opaque. 
