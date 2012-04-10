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

var toISOString = function(d) {
  return d.toISOString().replace(/-/g, "").replace(/:/g, "").replace(/\.\d\d\d/g, "");
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

Diffa.Models.Blobs = Backbone.Model.extend(Diffa.Collections.Watchable).extend({
  watchInterval: 5000,      // How frequently we poll for blob updates
  maxColumns: 96,           // Maybe make variable?
  defaultBucketSize: 3600,
  defaultZoomLevel:4,       // HOURLY
  defaultMaxRows: 10,       // Will change as more pairs arrive

  initialize: function() {
    _.bindAll(this, "sync", "stopPolling", "startPolling");

    this.set({
      zoomLevel: this.defaultZoomLevel,
      bucketSize: this.defaultBucketSize,
      swimlaneLabels: [],
      buckets: [],
      maxRows: this.defaultMaxRows,
      polling: true,
      startTime: nearestHour().add({seconds: -1 * this.defaultBucketSize * this.maxColumns}),
      selectedCell: null
    });
    this.domain = this.get('domain');   // Pull the domain out as a top-level attribute
  },

  sync: function() {
    // Don't do the poll if we're not polling
    if (!this.get('polling')) return;

    var self = this;

    var endTime = nearestHour();

    var now = toISOString(endTime);

    var startTime = endTime.add({seconds: -1 * self.get('bucketSize') * ( self.maxColumns -1 ) });
    var dayBeforeNow = toISOString(startTime);

    $.getJSON("/domains/" + this.domain.id + "/diffs/tiles/" + self.get('zoomLevel') + "?range-start=" + dayBeforeNow + "&range-end=" + now, function(data) {
      var swimlaneLabels = self.get('swimlaneLabels').slice(0);     // Retrieve a cloned copy of the swimlane labels
      var buckets = [];
      var maxRows = self.get('maxRows');

      // update swimlane labels
      for (var pair in data) {
        // add label if it doesn't already exist
        if (swimlaneLabels.indexOf(pair) < 0)
          swimlaneLabels.push(pair);
      }
        // Only keep labels that are in the data. Truncate our number of bucket rows to match the number of lanes.
      swimlaneLabels = $.grep(swimlaneLabels, function(pair) { return data[pair]; });
      if (buckets.length > swimlaneLabels.length)
        buckets.splice(swimlaneLabels.length, buckets.length - swimlaneLabels.length);

      // copy data into buckets
      maxRows = Math.max(swimlaneLabels.length, maxRows);
      for (var i = 0; i < maxRows; i++) {
        var values = data[swimlaneLabels[i]];
        if (values) {
          buckets[i] = buckets[i] || [];
          for (var j = 0; j < self.maxColumns; j++)
            buckets[i][j] = values[j] || 0;
        } else {
          // if a pair wasn't in the results, initialize or keep existing data
          if (! buckets[i]) {
            buckets[i] = [];
            for (var j = 0; j < self.maxColumns; j++)
              buckets[i][j] = 0;
          }
        }
      }

      // Update the swimlane labels and buckets
      self.set({swimlaneLabels: swimlaneLabels, buckets: buckets, maxRows: maxRows, startTime: startTime});
    });
  },

  startPolling: function() {
    this.set({polling: true});
  },

  stopPolling: function() {
    this.set({polling: false});
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
        self.collection.domain.blobs.sync();
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
      console.log(url);

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

Diffa.Views.Heatmap = Backbone.View.extend({
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

  rightLimit: 0,
  o_x: 0,
  o_y: 0,

  highlighted: null,

  initialize: function() {
    _.bindAll(this, "render", "update", "mouseUp", "mouseMove", "mouseDown");

    $(document).mouseup(this.mouseUp);
    $(document).mousemove(this.mouseMove);

    this.model.watch($(this.el));

    this.model.bind('change:buckets',         this.update);
    this.model.bind('change:maxRows',         this.update);
    this.model.bind('change:polling',         this.update);

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

    return this;
  },

  update: function() {
    this.clearEverything();
    this.recalibrateHeatmap();
    this.o_x = -1 * this.rightLimit;
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
    this.rightLimit = (this.model.maxColumns * this.gridSize) - this.canvas.width;

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
    layer.height = Math.max(this.minRows, this.model.get('swimlaneLabels').length) * this.swimlaneHeight() + this.bottomGutter;
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
    var region_width = this.model.maxColumns * this.gridSize;
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
    var swimlaneLabels = this.model.get('swimlaneLabels');
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
      var leftCell = this.findCellWithVisibleBlob(viewportX, s - laneHeight, directions.left);
      if (this.nonEmptyCellExists(leftCell.row, 0, leftCell.column)) {
        this.drawArrow(this.underlayContext, directions.left, 10, s - (arrowHeight / 4) - (this.gridSize / 2), arrowWidth, arrowHeight);
      }
      var rightCell = this.findCellWithVisibleBlob(viewportX + this.canvas.width - 1, s - laneHeight, directions.right);
      if (this.nonEmptyCellExists(rightCell.row, rightCell.column + 1, this.model.maxColumns)) {
        this.drawArrow(this.underlayContext, directions.right, this.canvas.width - 10 - arrowWidth, s - (arrowHeight / 4) - (this.gridSize / 2), arrowWidth, arrowHeight);
      }
      lane++;
    }

    // draw "live" / "click to poll" text
    var pollText = this.model.get('polling') ? " LIVE " : " CLICK TO POLL ";
    var textWidth = this.underlayContext.measureText(pollText).width;
    var textSpacer = 20;
    this.underlayContext.fillStyle = colours.red;
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
    var startTime = this.model.get('startTime');
    var bucketSize = this.model.get('bucketSize');
    var zoomLevel = this.model.get('zoomLevel');
    this.scaleContext.font = "9px sans-serif";
    for (var sc = 0; sc < this.model.maxColumns; sc++) {
      if (sc % 3 == 0) {
        var tick = new Date(startTime.getTime() + (sc * bucketSize * 1000));
        this.scaleContext.fillText(tick.toString("dd/MM"), sc * this.gridSize, 10);
        this.scaleContext.fillText(tick.toString("HH:mm"), sc * this.gridSize, 20);
      }
    }
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

    if (cell.column < this.model.maxColumns && cell.row < this.model.get('maxRows')) {
      var cell_x = i + Math.floor(this.gridSize / 2);
      var cell_y = j + this.gutterSize + Math.floor(this.gridSize / 2);
      var bucketSize = this.model.get('buckets')[cell.row][cell.column];
      var maximum = Math.floor((this.gridSize - 1) / 2);

      var cappedSize = this.limit(this.transformBucketSize(bucketSize, maximum), maximum);
      var size = cappedSize.value;
      var isOverMaximum = cappedSize.limited;

      if (size > 0) {
        // if the size has been limited, draw the outline slightly thicker
        this.context.lineWidth = isOverMaximum ? 2 : 1;
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
      var value = this.model.get('buckets')[this.highlighted.row][this.highlighted.column];
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

  /**
   * Finds a cell with a fully- or partially-visible blob at the given coordinates.
   * The "dir" parameter controls whether blob visibility is determined with respect
   * to the left or right of the x position.
   */
  findCellWithVisibleBlob: function(x, y, dir) {
    var cell = this.coordsToCell({"x": x, "y": y});
    var radius = this.limit(this.model.get('buckets')[cell.row][cell.column], Math.floor((this.gridSize - 1) / 2));
    if (radius.value > 0) {
      var cutoff = this.cellToCoords(cell).x + (this.gridSize / 2) + (dir == directions.left ? radius.value : -1 * radius.value);
      if (dir == directions.left && x > cutoff) {
        // nudge to the right if the leftmost cell's blob is no longer visible
        cell.column++;
      } else if (dir == directions.right && x < cutoff) {
        // nudge to the left if the rightmost cell's blob is no longer visible
        cell.column--;
      }
    }
    return cell;
  },

  nonEmptyCellExists: function(row, startColumn, endColumn) {
    var cols = this.model.get('buckets')[row];
    for (var i = startColumn; i < endColumn; i++) {
      if (cols[i] > 0)
        return true;
    }
    return false;
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

  /**
   * Transforms the bucket value from the API into a value we can use for
   * sizing the blob in the heatmap.
   *
   *   API   -> Size
   *   0     -> 0
   *   1     -> m     (1)
   *   ...
   *   100   -> M     (2)
   *
   * M is the maximum size. m is the minimum display size.
   * Between (1) and (2), growth should be logarithmic based
   * on the endpoints. Inputs over 100 follow the log function,
   * to be limited in the caller.
   *
   * We'd like a function f on [0, 100] where
   *
   *   f(1)   = m,
   *   f(100) = M
   *
   * and where f(x) is in some way logarithmic. So let
   *
   *   f(x) = a + b*log(x).
   *
   * (Note this is equivalent to f(x) = a + b*log(c*x))
   *
   * and solve for a, b:
   *
   *   f(1)   = a = m
   *   f(100) = m + b*log(100) = M  =>  b = (M-m)/log(100)
   */
  transformBucketSize: function(size, maximum) {
    var minimumIn = 1; // anything non-zero below this gets raised to valueFloor
    var valueFloor = 2;
    var maximumCutOff = 100; // anything over this is capped to the maximum

    if (size == 0)             { return 0; }
    if (size <= minimumIn)     { return valueFloor; }

    var a = valueFloor;
    var b = (maximum - valueFloor)/Math.log(maximumCutOff);

    return a + b*Math.log(size);
  },

  /**
   * Limits a value to a given maximum value, somewhat like Math.min().
   * Returns an object with two properties: "value", the limited value and "limited",
   * which flags whether the original value was greater than the maximum value.
   */
  limit: function(value, maximum) {
    if (value <= maximum) {
      return {"value":value, "limited":false};
    }
    return {"value":maximum, "limited":true};
  },

  dragging: false,
  dragged: false,
  mouseDown: function(e) {
    dragging = e;
    dragged = false;
    e.target.style.cursor = "move";
    return false;
  },

  togglePolling: function(c) {
    // TODO: Re-enable

    if (c.x > this.toggleX && c.y < this.toggleY) {
      if (this.model.get('polling')) {
        this.model.stopPolling();
      } else {
        this.model.startPolling();
      }
    }
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
        this.togglePolling(c);
        c.x -= this.o_x;

        // Perform a navigation
        var cell = this.coordsToCell(c);
        var selectedPair = this.model.get('swimlaneLabels')[cell.row];
        var gridStartTime = this.model.get('startTime');
        var selectedIdx = cell.column;
        var bucketSize = this.model.get('bucketSize');

        // Workaround for daily granularity
        if (bucketSize == 86400) {
          selectedIdx--;
        }

        var selectionStartTime = new Date(gridStartTime.getTime() + (selectedIdx * bucketSize * 1000));
        var selectionEndTime = new Date(selectionStartTime.getTime() + (bucketSize * 1000));
        $(this.el).trigger('blob:selected', [selectedPair, toISOString(selectionStartTime), toISOString(selectionEndTime)]);
      }
    } else {
      if (Math.abs(this.o_x) >= this.rightLimit) {
        this.model.startPolling();
      }
    }
    this.dragged = false;
    e.target.style.cursor = "default";
  },

  mouseMove: function(e) {
    if (this.dragging) {
      this.model.stopPolling();
      this.dragged = true;
      this.clearEverything();
      var m_coords = this.coords(e);
      var d_coords = this.coords(this.dragging);
      this.o_x += m_coords.x - d_coords.x;
      if (this.o_x > 0) {
        this.o_x = 0;
      }

      if (Math.abs(this.o_x) > this.rightLimit) {
        this.o_x = -1 * this.rightLimit;
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

  mouseOver: function(e) {
    var c = this.coords(e);
    c.x -= this.o_x;
    var cell = this.coordsToCell(c);

    if (cell.row >= 0 && cell.row < this.model.get('maxRows') && cell.column >= 0 && cell.column < this.model.maxColumns) {
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

    this.$('.difflist-row').empty();   // Empty the current difflist out since we'll re-render everything

    this.model.forEach(function(diff) {
      var view = new Diffa.Views.DiffListItem({model: diff, collection: self.model});
      this.$('.difflist-row').append(view.render().el);
    });
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
  className: 'span-14',

  events: {
    "click": "select"
  },

  initialize: function() {
    _.bindAll(this, "render", "select", "updateSelected");

    this.collection.bind("change:selectedEvent", this.updateSelected);
  },

  render: function() {
    var time = new Date(this.model.get('detectedAt')).toString("HH:mm:ss");
    var date = new Date(this.model.get('detectedAt')).toString("dd/MM/yyyy");
    var row = $(this.el)
        .append("<div class='span-2'>" + date + "</div>")
        .append("<div class='span-2'>" + time + "</div>")
        .append("<div class='span-3 wrappable'>" + this.model.get('objId').pair.key + "</div>")
        .append("<div class='span-3 wrappable'>" + this.model.get('objId').id + "</div>");

    if (!this.model.get('upstreamVsn')) {
      row.append("<div class='span-4 last'>Missing from upstream</div>");
    }
    else if (!this.model.get('downstreamVsn')) {
      row.append("<div class='span-4 last'>Missing from downstream</div>");
    }
    else {
      row.append("<div class='span-4 last'>Data difference</div>");
    }

    this.updateSelected(this.collection.selectedEvent);

    return this;
  },

  select: function() {
    this.collection.selectEvent(this.model.id);
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
      this.$('.item1 pre').text('');
      this.$('.item2 pre').text('');

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

    this.$('.content-label').text('Content for item ID: ' + itemID);

    this.$('.item1 .upstreamLabel').text(upstreamLabel);
    this.$('.item1 .diff-hash').text(upstreamVersion);

    this.$('.item2 .downstreamLabel').text(downstreamLabel);
    this.$('.item2 .diff-hash').text(downstreamVersion);

    var ignoreButton = $('<button class="repair">Ignore</button>');
    this.$('.controllist').empty().append(ignoreButton).show();
    ignoreButton.click(function() {
      event.ignore();
    });


    function waitForOrDisplayContent(selector, content) {
      var busy = this.$(selector).prev();

      if (content == null) {
        this.$(selector).hide();
        busy.show();
      } else {
        this.$(selector).text(content).show();
        busy.hide();
      }
    }
    waitForOrDisplayContent(".item1 pre", upstreamContent);
    waitForOrDisplayContent(".item2 pre", downstreamContent);

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

function nearestHour() {
  var hours = (new Date()).getHours() + 1;
  return Date.today().add({hours: hours});
}

$('.diffa-heatmap').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.Heatmap({el: $(this), model: domain.blobs});
});
$('.diffa-difflist').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.DiffList({el: $(this), model: domain.diffs});
});
$('.diffa-contentviewer').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.DiffDetail({el: $(this), model: domain.diffs});
});

$('.diffa-heatmap-page').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));

  new Diffa.Routers.Blobs({domain: domain, el: $(this)});
  Backbone.history.start();
});
});