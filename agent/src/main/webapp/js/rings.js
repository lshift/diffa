/**
 * Copyright (C) 2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

$(function() {

Diffa.Views.Rings = Backbone.View.extend(Diffa.Helpers.Viz).extend({
  colours: {
    current: {          // Orange
      line: '#ff9440',
      fill: '#ffb073'
    },
    previous: {         // Yellow
      line: '#f4fb40',
      fill: '#fafda4'
    },
    before: {           // Blue
      line: '#43adde',
      fill: '#99d3ed'
    },

    hovered: {          // Grey
      line: '#8e9690',
      fill: '#c3c7c4'
    }
  },

  initialize: function() {
    var self = this;

    _.bindAll(this, 'update', 'onMouseOver', 'onMouseMove', 'onMouseOut', 'onClick');

    // Subscribe to aggregates for the various rings
    this.model.subscribeAggregate('currentHour', function() {
      return {startTime: self.nearestMinute().addHours(-1)};
    });
    this.model.subscribeAggregate('previousHour', function() {
      return {startTime: self.nearestMinute().addHours(-2), endTime: self.nearestMinute().addHours(-1)};
    });
    this.model.subscribeAggregate('before', function() {
      return {endTime: self.nearestMinute().addHours(-2)};
    });

    // Render the basic widget
    this.setupWidget();

    // Listen for events within the canvas
    $(this.layer).
      mouseover(this.onMouseOver).
      mousemove(this.onMouseMove).
      mouseout(this.onMouseOut).
      click(this.onClick);

    // Listen for changes to the various properties
    this.model.on('change:currentHour',   this.update);
    this.model.on('change:previousHour',  this.update);
    this.model.on('change:before',        this.update);

    // Request that the aggregates periodically update
    this.model.watch(this.el);
  },

  nearestMinute: function() {
    var now = new Date();
    var advance = (now.getSeconds() > 0) ? 1 : 0;

    return Date.today().add({hours: now.getHours(), minutes: now.getMinutes() + advance});
  },

  setupWidget: function() {
    $(this.el).html(JST['rings/ring']());
    this.resize();

    this.ringInfo = this.$('.ring-info');
  },

  resize: function() {
    this.layer = this.$('canvas.content')[0];
    this.width = $(this.el).innerWidth();
    
    this.layer.width = this.width;
    this.layer.height = this.width;

    this.context = this.layer.getContext("2d");
  },

  update: function() {
    var singleValue = function(l) { return (l || [0])[0]; };
    var self = this;
    
    this.clearCanvas();

    this.currentHour = singleValue(this.model.get('currentHour'));
    this.previousHour = singleValue(this.model.get('previousHour'));
    this.before = singleValue(this.model.get('before'));

    var bucketOpts = {
      inputMin: 1,
      inputMax: 100,
      outputMin: 10,
      outputMax: (this.width / 2) - 5
    };

    this.currentSize = this.transformBucketSize(this.currentHour + this.previousHour + this.before, bucketOpts);
    this.previousSize = this.transformBucketSize(this.previousHour + this.before, bucketOpts);
    this.beforeSize = this.transformBucketSize(this.before, bucketOpts);

    var maybeHilightCircle = function(size, normalColour, ringName) {
      self.drawCircle(size,
        (ringName == self.hoverRing) ?
          {line: normalColour.line, fill: self.colours.hovered.fill} :
          normalColour);
    };

    maybeHilightCircle(this.currentSize.value, this.colours.current, 'currentHour');
    maybeHilightCircle(this.previousSize.value, this.colours.previous, 'previousHour');
    maybeHilightCircle(this.beforeSize.value, this.colours.before, 'before');

    var hasDiffs = (this.currentHour + this.previousHour + this.before) == 0;
    this.$('.no-differences-panel').
      css('height', this.width).
      toggle(hasDiffs);
    $(this.layer).toggle(!hasDiffs);
  },

  clearCanvas: function() {
    this.layer.width = this.layer.width;
  },

  drawCircle: function(value, colours) {
    this.context.lineWidth = 2;
    this.context.strokeStyle = colours.line;
    this.context.fillStyle = colours.fill;
    this.context.beginPath();
    this.context.arc(this.width / 2, this.width / 2, value, 0, Math.PI * 2, false);
    this.context.closePath();
    this.context.fill();
    this.context.stroke();
  },

  onMouseOver: function(e) {
    this.onMouseMove(e);    // Treat entry and movement as the same event
  },
  onMouseMove: function(e) {
    var maybePuralDiffs = function(count) {
      if (count == 1) return count + " difference";
      return count + " differences";
    };

    var globalPos = this.pageCoords(e);
    var widgetPos = this.widgetCoords(globalPos);

    // Work out if we're within the bounds of our widget
    var msg = null;
    var previousHoverRing = this.hoverRing;
    if (this.isInCircle(this.beforeSize.value, widgetPos)) {
      msg = maybePuralDiffs(this.before) + " more than 2 hours ago";
      this.hoverRing = 'before';
    } else if (this.isInCircle(this.previousSize.value, widgetPos)) {
      msg = maybePuralDiffs(this.previousHour) + " between 1 and 2 hours ago";
      this.hoverRing = 'previousHour';
    } else if (this.isInCircle(this.currentSize.value, widgetPos)) {
      msg = maybePuralDiffs(this.currentHour) + " in the last hour";
      this.hoverRing = 'currentHour';
    } else {
      this.hoverRing = null;
    }

    if (msg) {
      this.showDetails(msg, globalPos);
    } else {
      this.hideDetails();
    }

    if (previousHoverRing != this.hoverRing) {
      this.update();
    }
  },
  onMouseOut: function(e) {
    this.hideDetails();

    if (this.hoverRing) {
      this.hoverRing = null;
      this.update();
    }
  },
  onClick: function(e) {
    if (!this.hoverRing) return;      // Ignore clicks if we're not hovering anything

    // If we're hovering a ring, generate an event
    var details = $.extend({pair: this.model.id, ring: this.hoverRing}, this.model.lastRequests[this.hoverRing]);
    $(this.el).trigger('ring-clicked', [details]);
  },

  pageCoords: function(e) {
    if (e.pageX != undefined && e.pageY != undefined) {
      return {x: e.pageX, y: e.pageY};
    } else {
      return {
        x: e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft,
        y: e.clientY + document.body.scrollTop + document.documentElement.scrollTop
      };
    }
  },
  widgetCoords: function(coords) {
    return {x: coords.x - this.layer.offsetLeft, y: coords.y - this.layer.offsetTop};
  },

  isInCircle: function(radius, point) {
    var circleX = this.width / 2, circleY = this.width / 2;

    return Math.pow(point.x - circleX, 2) + Math.pow(point.y - circleY, 2) < Math.pow(radius, 2);
  },

  showDetails: function(text, position) {
    this.ringInfo.
      text(text).
      css('left', position.x).
      css('top', position.y).
      show();
  },
  hideDetails: function() {
    this.ringInfo.hide();
  }
});

Diffa.Views.RingSet = Backbone.View.extend({
  template: window.JST['rings/ringset-entry'],

  initialize: function() {
    _.bindAll(this, "addPair", "addAllPairs");

    this.model.pairs.bind("add", this.addPair);
    this.model.pairs.bind("reset", this.addAllPairs);
    this.addAllPairs();
  },

  addAllPairs: function() {
    this.model.pairs.each(this.addPair);
  },

  addPair: function(pair) {
    var pairEl = $(this.template({key: pair.id})).appendTo($(this.el));
    new Diffa.Views.Rings({el: $('.diffa-rings', pairEl), model: this.model.aggregates.forPair(pair.id)});
  }
});

$('.diffa-rings').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  var pairKey = $(this).data('pair');

  new Diffa.Views.Rings({el: $(this), model: domain.aggregates.forPair(pairKey)});
});

$('.diffa-ringset').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  var zoomHref = $(this).data('zoom-href');
  var zoomInLightbox = !!$(this).data('zoom-with-lightbox');
  
  new Diffa.Views.RingSet({el: $(this), model: domain});
  if (zoomHref) {
    $(this).bind('ring-clicked', function(e, details) {
      var targetUrl = zoomHref + '?domain=' + domain.id + '&pair=' + details.pair;
      if (details.startTime) {
        targetUrl += "&startTime=" + Diffa.Helpers.DatesHelper.toISOString(details.startTime);
      }
      if (details.endTime) {
        targetUrl += "&endTime=" + Diffa.Helpers.DatesHelper.toISOString(details.endTime);
      }

      if (!zoomInLightbox) {
        window.location = targetUrl;
      } else {
        $.colorbox({
          iframe: true,
          href: targetUrl,
          width: '90%',
          height: '90%'
        });
      }
    });
  }
  
  domain.loadAll(['pairs'], function() {});
});


});