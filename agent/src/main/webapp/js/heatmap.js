/**
 * Copyright (C) 2010-2011 LShift Ltd.
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
var directions = {
  left: 'left',
  right: 'right'
};

var colours = {
  black: 'black',
  darkGrey: '#555555',
  red: '#d12f19',
  transparent: 'rgba(0,0,0,0)',
  white: 'white'
};

Diffa.Routers.Blobs = Backbone.Router.extend({
  routes: {
    "":                             "index",     // #
    "blobs/:pair/:start-:end":      "viewBlob"   // # blobs/WEB-1/20110801134500/3600/5
  },

  initialize: function(opts) {
    var self = this;
    this.domain = opts.domain;

    opts.el.on('blob:selected', function(event, selectedPair, startTime, endTime) {
      self.navigate("blobs/" + selectedPair + '/' + startTime + '-' + endTime, true);
    });
  },

  index: function() {
  },

  viewBlob: function(pairKey, start, end) {
    // Currently, only the Diff list displays selection. When #320 is done, this will also need to inform the heatmap.
    this.domain.diffs.select(pairKey, start, end);
  }
});

Diffa.Models.HeatmapProjection = Backbone.Model.extend(Diffa.Collections.Watchable).extend({
  watchInterval: 5000,      // How frequently we poll for blob updates
  defaultZoomLevel:4,       // HOURLY
  defaultMaxRows: 10,       // Will change as more pairs arrive
  defaultBucketCount: 31,   // Default number of buckets. Will be overriden once heatmap is ready

  initialize: function() {
    _.bindAll(this, "sync");

    this.set({
      zoomLevel: this.defaultZoomLevel,
      bucketSize: this.calculateBucketSize(this.defaultZoomLevel),
      maxRows: this.defaultMaxRows,
      lastEndTime: nearestHour(),
      bucketCount: this.defaultBucketCount
    });
    this.aggregates = this.get('aggregates');   // Pull the aggregates collection out as a top-level attribute

    var self = this;
    var fireBucketChange = function() { self.trigger('change:buckets'); };
    this.aggregates.on('add', fireBucketChange);
    this.aggregates.on('change', fireBucketChange);

    // The two different end time properties should event out as changes to the start time
    this.on('change:fixedEndTime', function() { self.trigger('change:startTime'); });
    this.on('change:lastEndTime', function() { self.trigger('change:startTime'); });
  },

  sync: function() {
    var self = this;

    var endTime = nearestHour();
    if (this.get('fixedEndTime')) {
      endTime = this.get('fixedEndTime');
    }

    var startTime = this.startTimeFromEndTime(endTime);

    this.aggregates.subscribeAggregate('map', {startTime: startTime, endTime: endTime, bucketing: (self.get('bucketSize') / 60)});
    this.aggregates.subscribeAggregate('left', {endTime: startTime});
    this.aggregates.subscribeAggregate('right', {startTime: endTime});

    this.aggregates.sync(function() {
      self.set({'lastEndTime': endTime});
      self.aggregates.change();   // Queue all change events till everything is completed
    }, {silent: true});
  },

  zoomOut: function() {
    this.maybeUpdateZoomLevel(-1);
  },
  zoomIn: function() {
    this.maybeUpdateZoomLevel(1);
  },

  maybeUpdateZoomLevel: function(factor) {
    var newZoomLevel = this.get('zoomLevel') + factor;
    if(this.isZoomLevelValid(newZoomLevel)) {
      this.set({ zoomLevel: newZoomLevel });
      this.set({ bucketSize: this.calculateBucketSize(newZoomLevel) });
      this.sync();
    }
  },

  getBucketSize: function() {
    return this.calculateBucketSize(this.get('zoomLevel'));
  },

  calculateBucketSize: function(zoomLevel) {
    switch(zoomLevel) {
      case 0 : return 24 *60 * 60;  // DAILY
      case 1 : return 8 * 60 * 60;  // EIGHT HOURLY
      case 2 : return 4 * 60 * 60;  // FOUR HOURLY
      case 3 : return 2 * 60 * 60;  // TWO HOURLY
      case 4 : return 60 * 60;      // HOURLY
      case 5 : return 30 * 60;      // HALF HOURLY
      case 6 : return 15 * 60;      // QUARTER HOURLY
      default: null
    }
  },

  isZoomLevelValid: function(zoomLevel) {
    return zoomLevel <= 6 && zoomLevel >= 0;
  },

  getSwimlaneLabels: function() {
    return this.aggregates.pluck('pair');
  },

  getRow: function(row) {
    if (this.aggregates.length > row) {
      var pairAggs = this.aggregates.at(row).get('map') || [];
      var bucketCount = this.get('bucketCount');

      // Determine how many buckets different the projection is to the currently loaded data
      var timeOffsetBuckets = (this.get('lastEndTime').getTime() - this.getProjectionEndTime().getTime()) / 1000 / this.get('bucketSize');
      var lengthOffsetBuckets = bucketCount - pairAggs.length;
      var offsetBuckets = timeOffsetBuckets + lengthOffsetBuckets;
      
      if (offsetBuckets == 0) {
        return pairAggs;
      } else if (Math.abs(offsetBuckets) >= bucketCount) {
        // The aggregates are completely out of range. Return an empty array.
        return [];
      } else if (offsetBuckets > 0) {
        // We need to insert 0 entries
        var prefix = [];
        for (var i = 0; i < offsetBuckets; ++i) prefix.push(0);

        return prefix.concat(pairAggs).slice(0, bucketCount);
      } else {
        return pairAggs.slice(-offsetBuckets);
      }
    } else {
      return [];
    }
  },

  getLeftCount: function(row) {
    if (this.aggregates.length > row) {
      return (this.aggregates.at(row).get('left') || [0])[0];
    } else {
      return 0;
    }
  },

  getRightCount: function(row) {
    if (this.aggregates.length > row) {
      return (this.aggregates.at(row).get('right') || [0])[0];
    } else {
      return 0;
    }
  },

  getProjectionStartTime: function() {
    return this.startTimeFromEndTime(this.getProjectionEndTime());
  },

  getProjectionEndTime: function() {
    if (this.get('fixedEndTime')) return this.get('fixedEndTime');
    return this.get('lastEndTime');
  },

  startTimeFromEndTime: function(endTime) {
    return new Date(endTime.getTime() - this.get('bucketSize') * this.get('bucketCount') * 1000)
  },

  scrollView: function(offset) {
    if (!this.get('fixedEndTime')) this.set({fixedEndTime: this.get('lastEndTime')}, {silent: true});

    var newFixedTime = new Date(this.get('fixedEndTime').getTime() + offset * 1000);
    if (newFixedTime.getTime() > this.rightLimit().getTime()) {
      this.unset('fixedEndTime');
    } else {
      this.set({fixedEndTime: newFixedTime});
    }
  },

  isAtRightLimit: function() {
    return !this.has('fixedEndTime');
  },

  rightLimit: function() {
    return nearestHour();
  }
});

Diffa.Models.Diff = Backbone.Model.extend({
  pendingUpstreamRequest: null,
  pendingDownstreamRequest: null,

  initialize: function() {
    _.bindAll(this, "retrieveDetails", "ignore");
  },

  /**
   * Fill out this diff with more expensive-to-capture details, such as upstream/downstream content.
   */
  retrieveDetails: function() {
    var self = this;

    // Only retrieve the pair info if we don't already have it
    if (!self.get('upstreamName') || !self.get('downstreamName')) {
      $.get("/domains/" + self.collection.domain.id + "/config/pairs/" + this.get('objId').pair.key, function(data, status, xhr) {
        self.set({upstreamName: data.upstreamName, downstreamName: data.downstreamName});
      });
    }

    // Always retrieve the latest content for the content panels
    var getContent = function(field, upOrDown, pendingRequest) {
      if (pendingRequest) pendingRequest.abort();

      function setContent(content) {
        var attrs = {};
        attrs[field] = content;
        self.set(attrs);
      }

      pendingRequest = $.ajax({
            url: "/domains/" + self.collection.domain.id + "/diffs/events/" + self.id + "/" + upOrDown,
            success: function(data) {
              setContent(data || "no content found for " + upOrDown);
            },
            error: function(xhr, status, ex) {
              if (status != "abort") {
                if(console && console.log)
                  console.log('error getting the content for ' + upOrDown, status, ex, xhr);

                setContent("Content retrieval failed");
              }
            }
          });
      return pendingRequest;
    };

    this.pendingUpstreamRequest = getContent("upstreamContent", "upstream", this.pendingUpstreamRequest);
    this.pendingDownstreamRequest = getContent("downstreamContent", "downstream", this.pendingDownstreamRequest);
  },

  /**
   * Instructs the agent to ignore this difference.
   */
  ignore: function() {
    var self = this;

    $.ajax({
      url: "/domains/" + this.collection.domain.id + "/diffs/events/" + this.id,
      type: 'DELETE',
      success: function(data) {
        self.collection.domain.aggregates.sync();
        self.collection.domain.diffs.sync();
      },
      error: function(xhr, status, ex) {
        // TODO: 
      }
    });
  }
});

Diffa.Collections.Diffs = Diffa.Collections.CollectionBase.extend({
  watchInterval: 5000,      // How frequently we poll for diff updates
  range: null,
  page: 0,
  listSize: 20,
  selectedEvent: null,
  model: Diffa.Models.Diff,
  totalEvents: 0,
  totalPages: 0,
  lastSeqId: null,

  initialize: function(models, opts) {
    _.bindAll(this, "sync", "select", "selectEvent", "selectNextEvent");

    this.domain = opts.domain;
  },

  sync: function(force) {
    var self = this;

    if (this.range == null) {
      this.reset([]);
    } else {
      var url = "/domains/" + self.domain.id + "/diffs?pairKey=" + this.range.pairKey + "&range-start="
          + this.range.start + "&range-end=" + this.range.end
          + "&offset=" + (this.page * this.listSize) + "&length=" + this.listSize;

      $.get(url, function(data) {
        if (!force && data.seqId == self.lastSeqId) return;

        var diffs = _.map(data.diffs, function(diffEl) { diffEl.id = diffEl.seqId; return diffEl; });

        if (self.totalEvents != data.total) {
          self.totalEvents = data.total;
          self.totalPages = Math.ceil(self.totalEvents / self.listSize);
          self.trigger("change:totalEvents", self);
        }

        // Apply updates to the diffs that we currently have
        var newDiffEls = _.map(diffs, function(diff) {
          var current = self.get(diff.seqId);
          if (current == null) {
            return diff;
          } else {
            current.set(diff);    // Apply changes to the difference
            return current;
          }
        });
        self.reset(newDiffEls);

        // Select the first event when we don't have anything selected, or when the current selection is no longer
        // valid
        if (self.selectedEvent == null || !self.get(self.selectedEvent.id)) {
          if (diffs.length > 0)
            self.selectEvent(diffs[0].seqId);
          else
            self.selectEvent(null);
        }

        // If we're now beyond the last page, then scroll back to it
        if (self.page >= self.totalPages && self.totalPages > 0) {
          self.setPage(self.totalPages - 1, true);
        }

        self.lastSeqId = data.seqId;
      });
    }
  },

  select: function(pairKey, start, end) {
    this.range = {
      pairKey: pairKey,
      start: start,
      end: end
    };
    this.setPage(0, true);
  },

  selectEvent: function(evtId) {
    this.selectedEvent = this.get(evtId);
    this.trigger("change:selectedEvent", this.selectedEvent);
  },

  selectNextEvent: function() {
    this.selectEventWithOffset(1);
  },

  selectPreviousEvent: function() {
    this.selectEventWithOffset(-1);
  },

  selectEventWithOffset: function(offset) {
    if (this.selectedEvent != null) {
      var selectedIdx = this.indexOf(this.selectedEvent);
      var newIdx = selectedIdx + offset;
      if (newIdx >= 0 && newIdx < this.length) {
        var nextEvent = this.at(newIdx);
        if (nextEvent != null) {
          this.selectEvent(nextEvent.id);
        }
      }
    }
  },

  nextPage: function() {
    if (this.page < this.totalPages) this.setPage(this.page + 1);
  },

  previousPage: function() {
    if (this.page > 0) this.setPage(this.page - 1);
  },

  setPage: function(page, force) {
    if (force || this.page != page) {
      this.page = page;
      this.trigger("change:page", this);

      this.sync(true);
    }
  }
});

Diffa.Views.Heatmap = Backbone.View.extend(Diffa.Helpers.Viz).extend({
  minRows: 5,         // Minimum number of rows to be displayed

  // The original version of the heatmap was statically sized to 800x400 with 5 swimlanes @ 78 plus a 10 pixel gutter
  // When #232 lands, this will probably be calculated differently.
  bottomGutter: 10,
  gutterSize: 24,
  gridSize: 30,
  scaleHeight: 40,

  toggleX: false,
  toggleY: false,
  show_grid: false,

  o_x: 0,
  o_y: 0,

  highlighted: null,

  initialize: function() {
    _.bindAll(this, "render", "update", "pollAndUpdate", "mouseUp", "mouseMove", "mouseDown");

    $(document).mouseup(this.mouseUp);
    $(document).mousemove(this.mouseMove);

    this.model.watch($(this.el));

    this.model.bind('change:buckets', this.update);

    this.render();
    this.zoomControls = new Diffa.Views.ZoomControls({el: this.$('.heatmap-controls'), model: this.model});

    // Attach a mousedown handler to the overlay
    this.overlay.onmousedown = this.mouseDown;
  },

  render: function() {
    $(this.el).html(JST['heatmap/map']());

    this.heatmap = $(this.el)[0];
    this.underlay = this.$('.underlay')[0];
    this.scale = this.$(".scale")[0];

    this.resizeLayer(this.underlay, this.underlay.offsetWidth);
    this.canvas = this.createLayer(this.heatmap, 2);
    this.overlay = this.createLayer(this.heatmap, 4);

    this.context = this.canvas.getContext("2d");
    this.overlayContext = this.overlay.getContext("2d");
    this.underlayContext = this.underlay.getContext("2d");
    this.scaleContext = this.scale.getContext("2d");

    this.update();

    return this;
  },

  pollAndUpdate: function() {
    this.update();
    this.model.sync();
  },

  update: function() {
    this.clearEverything();
    this.recalibrateHeatmap();
    this.context.translate(this.o_x, this.o_y);
    this.scaleContext.translate(this.o_x, this.o_y);
    this.drawGrid();
  },

  clearEverything: function() {
    this.clearCanvas();
    this.clearOverlay();
    this.clearUnderlay();
    this.clearScale();
  },

  clearCanvas: function() { this.canvas.width = this.canvas.width; },
  clearOverlay: function() { this.overlay.width = this.overlay.width; },
  clearUnderlay: function() { this.underlay.width = this.underlay.width; },
  clearScale: function() { this.scale.width = this.scale.width; },

  calibrateHeatmap: function() {
    this.scale.width = this.scale.offsetWidth;
    this.scale.height = this.scaleHeight;
    this.visibleColumns = this.truncateInt(this.canvas.width / this.gridSize);
    this.model.set({bucketCount: this.visibleColumns});

    this.$('.heatmap-controls').
        show().
        css('top', $(this.heatmap).offset().top + 20).
        css('left', $(this.heatmap).offset().left - this.$('.heatmap-controls')[0].offsetWidth);
  },
  recalibrateHeatmap: function() {
    this.resizeLayer(this.underlay, this.underlay.offsetWidth);
    this.resizeLayerFromParent(this.canvas, this.underlay);
    this.resizeLayerFromParent(this.overlay, this.underlay);
    this.calibrateHeatmap();
  },

  resizeLayer: function(layer, width) {
    layer.width = width;
    layer.height = Math.max(this.minRows, this.model.getSwimlaneLabels().length) * this.swimlaneHeight() + this.bottomGutter;
  },
  resizeLayerFromParent: function(layer, parent) {
    var parentOffset = $(parent).offset();

    layer.style.position = "absolute";
    layer.style.left = parentOffset.left.toString() + "px";
    layer.style.top = parentOffset.top.toString() + "px";

    this.resizeLayer(layer, parent.offsetWidth);
  },
  swimlaneHeight: function() { return 2 * this.gutterSize + this.gridSize; },

  createLayer: function(parent, z_index) {
    var layer = document.createElement("canvas");
    document.body.appendChild(layer);
    layer.style.zIndex = z_index;
    this.resizeLayerFromParent(layer,parent);
    return layer;
  },

  drawGrid: function() {
    var region_width = this.visibleColumns * this.gridSize;
    // draw grid lines
    if (this.show_grid) {
      for (var x = 0.5; x < region_width; x += this.gridSize) {
        this.context.moveTo(x, 0);
        this.context.lineTo(x, this.canvas.height);
      }
      for (var y = 0.5; y < this.canvas.height; y += (2 * this.gutterSize + this.gridSize)) {
        this.context.moveTo(0, y);
        this.context.lineTo(region_width, y);
      }
      this.context.strokeStyle = colours.red;
      this.context.stroke();
    }

    // draw swim lanes
    var swimlaneLabels = this.model.getSwimlaneLabels();
    var lane = 0;
    var laneHeight = this.swimlaneHeight();
    var arrowWidth = 18;
    var arrowHeight = 12;
    var viewportX = this.o_x;
    viewportX = Math.abs(viewportX);// workaround for a bug in Chrome, Math.abs sometimes gets optimized away or otherwise borked
    for (var s = 0.5 + laneHeight; s < this.canvas.height; s += laneHeight) {
      this.dashedLine(this.underlayContext, 0, s, this.canvas.width, s, 2);
      if (swimlaneLabels[lane] != null) {
        this.underlayContext.font = "11px 'Lucida Grande', Tahoma, Arial, Verdana, sans-serif";
        this.underlayContext.fillStyle = colours.black;
        this.underlayContext.fillText(swimlaneLabels[lane], 10, s - laneHeight + arrowHeight);
      }

      // Draw arrows if we have values outside the map for this row
      var cell = this.coordsToCell({"x": viewportX, "y": s - laneHeight});
      if (this.model.getLeftCount(cell.row)) {
        this.drawArrow(this.underlayContext, directions.left, 10, s - (arrowHeight / 4) - (this.gridSize / 2), arrowWidth, arrowHeight);
      }
      if (this.model.getRightCount(cell.row)) {
        this.drawArrow(this.underlayContext, directions.right, this.canvas.width - 10 - arrowWidth, s - (arrowHeight / 4) - (this.gridSize / 2), arrowWidth, arrowHeight);
      }
      lane++;
    }

    // draw "live" / "click to poll" text
    var isLive = this.model.isAtRightLimit() && this.o_x == 0;
    var pollText = isLive ? " LIVE " : " LOCKED ";
    var textWidth = this.underlayContext.measureText(pollText).width;
    var textSpacer = 20;
    this.underlayContext.fillStyle = isLive ? colours.red : colours.darkGrey;
    this.underlayContext.fillRect(this.canvas.width - textWidth - textSpacer, 0, textWidth + textSpacer, 20);
    this.underlayContext.fillStyle = colours.white;
    this.underlayContext.font = "12px 'Lucida Grande', Tahoma, Arial, Verdana, sans-serif";
    this.underlayContext.textBaseline = "top";
    this.underlayContext.fillText(pollText, this.canvas.width - this.underlayContext.measureText(pollText).width - (textSpacer / 2), 5);
    this.toggleX = this.canvas.width - textWidth - textSpacer;
    this.toggleY = 20;

    // draw circles
    for (var i = 0.5; i < region_width; i += this.gridSize) {
      for (var j = 0.5; j < this.canvas.height; j += (2 * this.gutterSize + this.gridSize)) {
        this.drawCircle(i, j);
      }
    }

    // draw scale
    var every = 3;      // At what column intervals labels will be placed
    var startTime = this.model.getProjectionStartTime();
    var bucketSize = this.model.get('bucketSize');
    var zoomLevel = this.model.get('zoomLevel');
    this.scaleContext.font = "9px sans-serif";
    var alignedStart = this.align(startTime, every);
    var alignOffset = (alignedStart.getTime() - startTime.getTime()) / 1000 / bucketSize * this.gridSize;

    // Iterate the columns, and place a label at the 'every' interval
    for (var sc = 0; sc < this.visibleColumns; sc++) {
      if (sc % every == 0) {
        var tick = new Date(alignedStart.getTime() + (sc * bucketSize * 1000));
        this.scaleContext.fillText(tick.toString("dd/MM"), sc * this.gridSize + alignOffset, 10);
        this.scaleContext.fillText(tick.toString("HH:mm"), sc * this.gridSize + alignOffset, 20);
      }
    }
  },

  align: function(time, skip) {
    var millis = time.getTime();
    var divisions = this.model.get('bucketSize') * skip;
    var next = Math.ceil(millis / 1000 / divisions) * divisions * 1000;
    return new Date(next);
  },

  dashedLine: function(ctx, x1, y1, x2, y2, dashLen) {
    if (dashLen == undefined) dashLen = 2;

    ctx.beginPath();
    ctx.moveTo(x1, y1);

    var dX = x2 - x1;
    var dY = y2 - y1;
    var dashes = Math.floor(Math.sqrt(dX * dX + dY * dY) / dashLen);
    var dashX = dX / dashes;
    var dashY = dY / dashes;

    var q = 0;
    while (q++ < dashes) {
      x1 += dashX;
      y1 += dashY;
      if (q % 2 == 0) {
        ctx.moveTo(x1, y1);
      }
      else {
        ctx.lineTo(x1, y1);
      }
    }
    if (q % 2 == 0) {
      ctx.moveTo(x1, y1);
    }
    else {
      ctx.lineTo(x1, y1);
    }

    ctx.stroke();
    ctx.closePath();
  },

  drawCircle: function(i, j) {
    var cell = this.coordsToCell({"x":i, "y":j});

    if (cell.column < this.visibleColumns && cell.row < this.model.get('maxRows')) {
      var cell_x = i + Math.floor(this.gridSize / 2);
      var cell_y = j + this.gutterSize + Math.floor(this.gridSize / 2);
      var bucketSize = this.model.getRow(cell.row)[cell.column] || 0;
      var maximum = Math.floor((this.gridSize - 1) / 2);

      var cappedSize = this.transformBucketSize(bucketSize, {
        inputMin: 1,
        inputMax: 100,
        outputMin: 2,
        outputMax: maximum
      });
      var size = cappedSize.value;
      var isOverMaximum = cappedSize.limited;

      if (size > 0) {
        // if the size has been limited, draw the outline slightly thicker
        this.context.lineWidth = isOverMaximum ? 3 : 2;
        this.context.strokeStyle = colours.black;
        this.context.fillStyle = colours.white;
        this.context.beginPath();
        this.context.arc(cell_x, cell_y, size, 0, Math.PI * 2, false);
        this.context.closePath();
        this.context.stroke();
        this.context.fill();
      }
    }
  },

  drawArrow: function(ctx, dir, x, y, w, h) {
    var headWidth = w / 2;
    var cornerHeight = h - (h / 4);

    var startX = x + (dir == directions.left ? 0 : w),
        headX  = x + (dir == directions.left ? headWidth : w - headWidth),
        endX   = x + (dir == directions.left ? w : 0);

    var gradient = this.context.createLinearGradient(startX, y, endX, y);
    gradient.addColorStop(0, colours.darkGrey);
    gradient.addColorStop(1, colours.transparent);

    ctx.save();
    ctx.strokeStyle = colours.transparent;
    ctx.fillStyle = gradient;
    ctx.beginPath();
    ctx.moveTo(startX, y + h / 2);
    ctx.lineTo(headX, y);
    ctx.lineTo(headX, y + cornerHeight);
    ctx.lineTo(endX,  y + cornerHeight);
    ctx.lineTo(endX, y + h - cornerHeight);
    ctx.lineTo(headX, y + h - cornerHeight);
    ctx.lineTo(headX, y + h);
    ctx.closePath();
    ctx.stroke();
    ctx.fill();
    ctx.restore();
  },

  drawOverlay: function() {
    if (this.highlighted != null && this.highlighted.column >= 0 && this.highlighted.row >= 0) {
      var value = this.model.getRow(this.highlighted.row)[this.highlighted.column];
      if (value > 0) {
        var c_x = this.highlighted.column * this.gridSize;
        var c_y = (this.highlighted.row * (2 * this.gutterSize + this.gridSize)) + this.gutterSize + this.gridSize;
        this.overlayContext.font = "12px sans-serif";
        this.overlayContext.textBaseline = "top";
        var width = this.context.measureText("" + value).width;
        this.overlayContext.fillText(value, c_x + Math.floor(this.gridSize / 2) - Math.floor(width / 2), c_y);
      }
    }
  },

  coords: function(e) {
    var x;
    var y;
    if (e.pageX != undefined && e.pageY != undefined) {
      x = e.pageX;
      y = e.pageY;
    }
    else {
      x = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
      y = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
    }

    x -= this.heatmap.offsetLeft;
    y -= this.heatmap.offsetTop;

    return { "x":x, "y":y };
  },

  coordsToCell: function(coords) {
    return {
      "row": Math.floor(coords.y / (2 * this.gutterSize + this.gridSize)),
      "column": Math.floor((coords.x) / this.gridSize)
    };
  },

  cellToCoords: function(cell) {
    return {
      "x": cell.column * this.gridSize,
      "y": cell.row * (2 * this.gutterSize + this.gridSize)
    };
  },

  dragging: false,
  dragged: false,
  mouseDown: function(e) {
    dragging = e;
    dragged = false;
    e.target.style.cursor = "move";
    return false;
  },


  mouseDown: function(e) {
    this.dragging = e;
    this.dragged = false;
    e.target.style.cursor = "move";
    return false;
  },

  mouseUp: function(e) {
    this.dragging = false;
    if (!this.dragged) {
      if (e.target.tagName == "CANVAS") {
        var c = this.coords(e);
        c.x -= this.o_x;

        // Perform a navigation
        var cell = this.coordsToCell(c);
        var selectedPair = this.model.getSwimlaneLabels()[cell.row];
        var gridStartTime = this.model.getProjectionStartTime();
        var selectedIdx = cell.column;
        var bucketSize = this.model.get('bucketSize');

        var selectionStartTime = new Date(gridStartTime.getTime() + (selectedIdx * bucketSize * 1000));
        var selectionEndTime = new Date(selectionStartTime.getTime() + (bucketSize * 1000));
        $(this.el).trigger('blob:selected', [selectedPair, Diffa.Helpers.DatesHelper.toISOString(selectionStartTime), Diffa.Helpers.DatesHelper.toISOString(selectionEndTime)]);
      }
    } else {
      this.pollAndUpdate();
    }
    this.dragged = false;
    e.target.style.cursor = "default";
  },

  mouseMove: function(e) {
    if (this.dragging) {
      this.dragged = true;
      this.clearEverything();
      var m_coords = this.coords(e);
      var d_coords = this.coords(this.dragging);
      this.o_x += m_coords.x - d_coords.x;

      // Calculate the number of cumulative buckets we've changed, along with the remaining positional offset
      var bucketsChange = this.truncateInt(this.o_x / this.gridSize);
      this.o_x %= this.gridSize;

      var secondsChange = (bucketsChange * this.model.getBucketSize());
      if (secondsChange != 0) {
        this.model.scrollView(-secondsChange);
      }

      if (this.model.isAtRightLimit() && this.o_x < 0) {
        this.o_x = 0;
      }

      this.context.translate(this.o_x, this.o_y);
      this.scaleContext.translate(this.o_x, 0);
      this.drawGrid();
      this.dragging = e;
    }
    else {
      this.clearOverlay();
      this.overlayContext.translate(this.o_x, this.o_y);
      this.mouseOver(e);
    }
  },

  truncateInt: function(d) {
    if (d < 0) return Math.ceil(d);
    return Math.floor(d);
  },

  mouseOver: function(e) {
    var c = this.coords(e);
    c.x -= this.o_x;
    var cell = this.coordsToCell(c);

    if (cell.row >= 0 && cell.row < this.model.get('maxRows') && cell.column >= 0 && cell.column < this.visibleColumns) {
      this.highlighted = cell;
      this.drawOverlay();
    }
  }
});

Diffa.Views.ZoomControls = Backbone.View.extend({
  events: {
    "click  .zoomIn":   "zoomIn",
    "click  .zoomOut":  "zoomOut",

    "focus  .zoomIn":   "preventFocus",
    "focus  .zoomOut":  "preventFocus"
  },

  initialize: function() {
    var self = this;

    _.bindAll(this, "render");

    this.model.bind("change:zoomLevel", this.render);

    $(document).keypress(function(e) {
      if (e.charCode == '+'.charCodeAt()) {
        e.preventDefault();
        if (self.shouldAllowMoreZoomIn()) self.zoomIn();
      }
      if (e.charCode == '-'.charCodeAt()) {
        e.preventDefault();
        if (self.shouldAllowMoreZoomOut()) self.zoomOut();
      }

      return true;
    });

    this.render();
  },

  render: function() {
    function toggleControl(selector, isDisabled) {
      if (isDisabled) {
        $(selector).attr('disabled', 'disabled');
      } else {
        $(selector).removeAttr('disabled');
      }
    }

    toggleControl('.zoomIn', !this.shouldAllowMoreZoomIn());
    toggleControl('.zoomOut', !this.shouldAllowMoreZoomOut());
  },

  shouldAllowMoreZoomIn: function() {
    return this.model.get('zoomLevel') < 6;   // Maximum zoom level is 6 - corresponds to a 15 minute granularity
  },
  shouldAllowMoreZoomOut: function() {
    return this.model.get('zoomLevel') > 0;  // Minimal zoom level is 0 - corresponds to a daily granularity
  },

  zoomOut: function() { this.model.zoomOut(); },
  zoomIn: function() { this.model.zoomIn(); },

  preventFocus: function(e) { $(e.target).blur(); }
});

Diffa.Views.DiffList = Backbone.View.extend({
  events: {
    "click .previous": "previousPage",
    "click .next":     "nextPage"
  },

  initialize: function() {
    var self = this;

    _.bindAll(this, "rebuildDiffList", "renderNavigation");

    this.model.watch($(this.el));

    this.model.bind("reset",              this.rebuildDiffList);
    this.model.bind("change:totalEvents", this.renderNavigation);
    this.model.bind("change:page",        this.renderNavigation);

    $(document).keydown(function(e) {
      if (e.keyCode == 38) {  // Up arrow
        e.preventDefault();
        self.model.selectPreviousEvent();
      }
      if (e.keyCode == 40) {    // Down arrow
        e.preventDefault();
        self.model.selectNextEvent();
      }
      if (e.keyCode == 37) {  // Left arrow
        e.preventDefault();
        self.model.previousPage();
      }

      if (e.keyCode == 39) {  // Right arrow
        e.preventDefault();
        self.model.nextPage();
      }

      return true;
    });

    $(this.el).html(JST['heatmap/difflist']());
    this.renderNavigation();
  },

  rebuildDiffList: function() {
    var self = this;

    this.$('.difflist-body').empty();   // Empty the current difflist out since we'll re-render everything

    this.model.forEach(function(diff) {
      var view = new Diffa.Views.DiffListItem({model: diff, collection: self.model});
      this.$('.difflist-body').append(view.render().el);
    });

    if ($('.difflist-body').children().length == 0) {
      $('.difflist-body').html("No differences.");
    }
  },

  renderNavigation: function() {
    var startIdx = (this.model.page * this.model.listSize) + 1;
    var endIdx = Math.min(startIdx + this.model.listSize - 1, this.model.totalEvents);

    this.$(".pagecount").text("Showing " + startIdx + " - " + endIdx + " of " + this.model.totalEvents + " differences");
    this.$(".navigation").toggle(this.model.totalPages > 1);
  },

  nextPage: function(e) { e.preventDefault(); this.model.nextPage(); },
  previousPage: function(e) { e.preventDefault(); this.model.previousPage(); }
});

Diffa.Views.DiffListItem = Backbone.View.extend({
  tagName: 'div',
  className: 'difflist-row',

  events: {
    "click": "select",
    "dblclick": "expand"
  },

  initialize: function() {
    _.bindAll(this, "render", "select", "updateSelected");

    this.collection.bind("change:selectedEvent", this.updateSelected);
  },

  render: function() {
    var time = new Date(this.model.get('detectedAt')).toString("HH:mm:ss");
    var date = new Date(this.model.get('detectedAt')).toString("dd/MM/yyyy");
    var row = $(this.el)
        .append("<div class='date-col'>" + date + "</div>")
        .append("<div class='time-col'>" + time + "</div>")
        .append("<div class='pairing-col wrappable'>" + this.model.get('objId').pair.key + "</div>")
        .append("<div class='item-id-col wrappable'>" + this.model.get('objId').id + "</div>");

    if (!this.model.get('upstreamVsn')) {
      row.append("<div class='difference-col last'>Missing from upstream</div>");
    }
    else if (!this.model.get('downstreamVsn')) {
      row.append("<div class='difference-col last'>Missing from downstream</div>");
    }
    else {
      row.append("<div class='difference-col last'>Data difference</div>");
    }

    this.updateSelected(this.collection.selectedEvent);

    return this;
  },

  select: function() {
    this.collection.selectEvent(this.model.id);
  },

  expand: function(e) {
    $(this.el).trigger('expand-event', [this.model]);

    // Double clicking results in the text being selected. We don't actually want that, so we'll
    // clear the selection.
    if(document.selection && document.selection.empty) {
        document.selection.empty();
    } else if(window.getSelection) {
        var sel = window.getSelection();
        sel.removeAllRanges();
    }
  },

  updateSelected: function(selectedEvent) {
    $(this.el).toggleClass("specific_selected", selectedEvent != null && selectedEvent.id == this.model.id)
  }
});

Diffa.Views.DiffDetail = Backbone.View.extend({
  lastSelected: null,

  initialize: function() {
    _.bindAll(this, "render", "updateSelected");

    this.model.bind("change:selectedEvent", this.updateSelected);

    var template = JST['heatmap/contentviewer'];

    $(this.el).html(template({API_BASE: API_BASE}));
    this.render();
  },

  updateSelected: function(newSelected) {
    if (this.lastSelected != newSelected) {
      // Swap the target of our event subscriptions
      if (this.lastSelected != null) this.lastSelected.bind("change", this.render);
      if (newSelected != null) newSelected.bind("change", this.render);

      // Record the last selected diff so we can cleanup event bindings
      this.lastSelected = newSelected;
      
      // Ensure that we have full details for the newly selected event
      if (newSelected != null) newSelected.retrieveDetails();

      this.render();
    }
  },

  render: function() {
    var event = this.model.selectedEvent;

    // Clear the state if we don't have a selected event
    if (event == null) {
      this.$('.content-label').text('No item selected');
      this.$('.item1 .upstreamLabel').text('upstream');
      this.$('.item1 .diff-hash').text('');
      this.$('.item2 .downstreamLabel').text('downstream');
      this.$('.item2 .diff-hash').text('');

      this.$(".controllist").hide();
      this.$(".actionlist").empty();
      return;
    }

    var itemID = event.get('objId').id,
        upstreamLabel = event.get('upstreamName') || "upstream",
        upstreamVersion = event.get('upstreamVsn') || "no version",
        downstreamLabel = event.get("downstreamName") || "downstream",
        downstreamVersion = event.get('downstreamVsn') || "no version",
        upstreamContent = event.get("upstreamContent"),
        downstreamContent = event.get("downstreamContent");

    this.$('.content-label').text('Versions for item ID: ' + itemID);

    this.$('.item1 .upstreamLabel').text(upstreamLabel);
    this.$('.item1 .diff-hash').text(upstreamVersion);

    this.$('.item2 .downstreamLabel').text(downstreamLabel);
    this.$('.item2 .diff-hash').text(downstreamVersion);

    var ignoreButton = $('<button class="repair">Ignore</button>');
    this.$('.controllist').empty().append(ignoreButton).show();
    ignoreButton.click(function() {
      event.ignore();
    });

    this.renderEntityScopedActions();
  },

  renderEntityScopedActions: function() {
    var event = this.model.selectedEvent;
    var self = this;

    var pairKey = event.get('objId').pair.key;
    var itemID = event.get('objId').id;
    var actionListContainer = this.$(".actionList").empty();
    var actionListCallback = function(actionList, status, xhr) {
      if (!actionList) {
        return;
      }
      
      self.$(".actionlist").empty();
      $.each(actionList, function(i, action) {
        var repairStatus = self.$('.repairstatus');
        appendActionButtonToContainer(actionListContainer, action, pairKey, itemID, repairStatus);
      });
    };

    $.ajax({ url: "/domains/" + this.model.domain.id + '/actions/' + pairKey + '?scope=entity', success: actionListCallback });
  }
});

Diffa.Views.DiffInspectorPopup = Backbone.View.extend({
  initialize: function() {
    var self = this;

    $(document).bind('expand-event', function(e, model) {
      // Attach the inspector view, and open a lightbox
      var inspector = new Diffa.Views.DiffInspector({model: model});
      $.colorbox({
        inline: true,
        href: $(inspector.el)
      });
    });

      // Keep our element invisible
    $(this.el).hide();
  }
});

Diffa.Views.DiffInspector = Backbone.View.extend({
  tagName: 'div',
  className: 'diffa-diffinspector',

  initialize: function() {
    _.bindAll(this, "render");

    $(this.el).html(JST['heatmap/contentinspector']({API_BASE: API_BASE}));
    this.render();
  },

  render: function() {
    var event = this.model;
    var self = this;

    var itemID = event.get('objId').id,
        upstreamLabel = event.get('upstreamName') || "upstream",
        upstreamVersion = event.get('upstreamVsn') || "no version",
        downstreamLabel = event.get("downstreamName") || "downstream",
        downstreamVersion = event.get('downstreamVsn') || "no version",
        upstreamContent = event.get("upstreamContent"),
        downstreamContent = event.get("downstreamContent");

    this.$('.content-label').text('Content for item ID: ' + itemID);

    this.$('.item1 .upstreamLabel').text(upstreamLabel);
    this.$('.item1 .diff-hash').text(upstreamVersion);

    this.$('.item2 .downstreamLabel').text(downstreamLabel);
    this.$('.item2 .diff-hash').text(downstreamVersion);

    function waitForOrDisplayContent(selector, content) {
      var busy = this.$(selector).prev();

      if (content == null) {
        self.$(selector).hide();
        busy.show();
      } else {
        self.$(selector).text(content).show();
        busy.hide();
      }
    }
    waitForOrDisplayContent(".item1 pre", upstreamContent);
    waitForOrDisplayContent(".item2 pre", downstreamContent);
  }
});


function nearestHour() {
  var hours = (new Date()).getHours() + 1;
  return Date.today().add({hours: hours});
}

$('.diffa-heatmap').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.Heatmap({el: $(this), model: new Diffa.Models.HeatmapProjection({aggregates: domain.aggregates})});
});
$('.diffa-difflist').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.DiffList({el: $(this), model: domain.diffs});
});
$('.diffa-contentviewer').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.DiffDetail({el: $(this), model: domain.diffs});
});
$('.diffa-contentinspector').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.DiffInspectorPopup({el: $(this)});
});

$('.diffa-heatmap-page').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));

  new Diffa.Routers.Blobs({domain: domain, el: $(this)});
  Backbone.history.start();
});
});