---
layout: default
title: Demo Participants
---

{% include links.md %}

# Diffa Demo Overview

For a quick overview you can view the simple demo application in action via the screencast. 
Please note you will need Flash enabled in your browser.


<object width="600" height="375"><param name="allowfullscreen" value="true" /><param name="allowscriptaccess" value="always" /><param name="movie" value="http://vimeo.com/moogaloop.swf?clip_id=15802963&amp;server=vimeo.com&amp;show_title=0&amp;show_byline=0&amp;show_portrait=0&amp;color=00ac3a&amp;fullscreen=1&amp;autoplay=0&amp;loop=0" /><embed src="http://vimeo.com/moogaloop.swf?clip_id=15802963&amp;server=vimeo.com&amp;show_title=0&amp;show_byline=0&amp;show_portrait=0&amp;color=00ac3a&amp;fullscreen=1&amp;autoplay=0&amp;loop=0" type="application/x-shockwave-flash" allowfullscreen="true" allowscriptaccess="always" width="600" height="375"></embed></object><p><a href="http://vimeo.com/15802963">Diffa Screencast</a> from <a href="http://vimeo.com/user4956615">0x6e6562</a> on <a href="http://vimeo.com">Vimeo</a>.</p>


### How The Demo Application Works

You can see how Diffa works in context without having to implement your own participants by using the Demo application. This pre-packaged demo supplies a generic example where two example participants are supplying data for comparison. The input data can be manually controlled from a web browser.  

The Demo shows you: 

* How to add upstream events and create inconsistencies
* How to bring the downstream back into line with the upstream

The main difference between the demo application and a typical production rollout is a web application that contains two remote controllable Diffa participants. These participants are remote controllable for the sake of being able to view the whole end to end interaction within a single browser:

![demo_image]

In this example, the two participants are implemented by two different threads of execution within a single web application. In addition to this, a simple UI is provided to facilitate the manipulation of each participant's internal state. 

### What's Going On

The moving parts in a generic deployment scenario will generally look like the following:

![generic]

In this diagram you can see the following components:

* The differencing agent;
* An upstream participant;
* A downstream participant;
* The user interface that allows you to view differences;

To see for yourself, [download][download] the Demo.
