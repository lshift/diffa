---
layout: default
title: Quick Start Guide

---

{% include links.md %}

# Integrating Developer's Guide To Repair Actions

This guide is designed to explain the purpose and mechanism of *repair actions*, how to configure them, and how to invoke them via the Diffa UI.

## Introduction

Imagine the situation: you have recently installed a Diffa agent and have configured it in a [health monitoring scenario][health_monitoring_guide]. The UI is alerting you to occasional differences in your data. When such a difference arises, it most often can be repaired by some repeatable process (for example, simply making the upstream system re-send the entity which was missing on the downstream system).

If you've already automated that process, then you're almost all the way to being able to invoke that process without leaving the Diffa UI. All you need to do is expose an HTTP endpoint to the Diffa agent, which accepts POST requests to tell the system to do its repair work.

## Anatomy of a Repair Action

Diffa's concept of a repair action comprises the following pieces of information:

 - **Name**: A concise, human-readable name for the action which is displayed on the Diffa UI.
 - **Scope**: One of `"pair"` or `"entity"`. Entity-scoped actions can be performed when you have clicked on a particular data difference in the UI, and pass the (missing/differing) entity's unique identifier to the system performing the repair action. Pair-scoped actions may be performed at any time, from the UI's settings page.
 - **URL**: The location of the HTTP endpoint. In the case of an entity-scoped repair action, the URL should contain a placeholder `{id}`, which is substituted with an entity's identifier before the agent makes a request.

All repair actions belong to a [pair][], so the button to invoke an entity-scoped action appears when viewing any data differences in its pair, and the button for a pair-scoped action appears next to that pair on the settings page.

## Configuring a Repair Action

Repair actions can be configured using Diffa's [REST API][rest], either using JSON or by sending the full XML config.

