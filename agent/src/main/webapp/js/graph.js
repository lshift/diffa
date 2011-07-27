
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

var heatmap;
var canvas;
var context;
var overlay;
var overlayContext;
var underlay;
var underlayContext;
var scale;
var scaleContext;
var buckets = [];
var maxColumns = 96;// N.B. currently a constant
var minRows = 5;
var maxRows = 10;// N.B. variable as more pair data arrives
var gridSize = 30;
var gutterSize = 24;

var scaleHeight = 40;

// The original version of the heatmap was statically sized to 800x400 with 5 swimlanes @ 78 plus a 10 pixel gutter
// When #232 lands, this will probably be calculated differently.
var bottomGutter = 10;

var rightLimit = 0;
var selectedBucket;

var colours = {
  black: 'black',
  darkGrey: '#555555',
  red: '#d12f19',
  fadedRed: '#d19e97',
  transparent: 'rgba(0,0,0,0)',
  white: 'white',
  yellow: '#FFF2CC'
}

var directions = {
  left: 'left',
  right: 'right'
}

// These global variables store pending requests so that all entity detail requests can be handled in a LIFO
// fashion by aborting the previous request
var pendingUpstreamRequest;
var pendingDownstreamRequest;


$(document).ready(function() {
  initGraph();
});

$(window).resize(function() {
  stopPolling();
  clearEverything();
  recalibrateHeatmap();
  startPolling();
});

/**
 * This is a once off intialization of the heatmap layers.
 * Note that if this function is called multiple times, which it shouldn't, new layers are added to the DOM,
 * without removing any previously created layers.
 */
function initCanvas() {
  heatmap = document.getElementById("heatmap");
  underlay = document.getElementById("underlay");
  scale = document.getElementById("scale");

  resizeLayer(underlay, underlay.offsetWidth);
  canvas = createLayer(heatmap, 2);
  overlay = createLayer(heatmap, 4);

  context = canvas.getContext("2d");
  overlayContext = overlay.getContext("2d");
  underlayContext = underlay.getContext("2d");
  scaleContext = scale.getContext("2d");

  calibrateHeatmap();
}

/**
 * Sets global objects to an appropriate scale.
 */
function calibrateHeatmap() {
  scale.width = scale.offsetWidth;
  scale.height = scaleHeight;
  rightLimit = (maxColumns * gridSize) - canvas.width;

  $('#heatmap-controls').
      show().
      css('top', $('#heatmap').offset().top + 20).
      css('left', $('#heatmap').offset().left - $('#heatmap-controls')[0].offsetWidth);
}

/**
 * Performs a calibration in addition to rescaling the three main canvas layers
 */
function recalibrateHeatmap() {
  resizeLayer(underlay, underlay.offsetWidth);
  resizeLayerFromParent(canvas, underlay);
  resizeLayerFromParent(overlay, underlay);
  calibrateHeatmap();
}

function createLayer(parent, z_index) {
  var layer = document.createElement("canvas");
  document.body.appendChild(layer);
  layer.style.zIndex = z_index;
  resizeLayerFromParent(layer,parent);
  return layer;
}

// TODO consider patching this in
function resizeLayer(layer, width) {
  layer.width = width;
  layer.height = Math.max(minRows, swimlaneLabels.length) * swimlaneHeight() + bottomGutter;
}

function resizeLayerFromParent(layer, parent) {
  var parentOffset = $(parent).offset();

  layer.style.position = "absolute";
  layer.style.left = parentOffset.left;
  layer.style.top = parentOffset.top;
  resizeLayer(layer, parent.offsetWidth);
}

function swimlaneHeight() {
  return 2 * gutterSize + gridSize;
}

function clearCanvas() {
  canvas.width = canvas.width;
}

function clearOverlay() {
  overlay.width = overlay.width;
}

function clearUnderlay() {
  underlay.width = underlay.width;
}

function clearScale() {
  scale.width = scale.width;
}

var sessionId = null;

function createSession(withValidSessionId) {
  var handleSessionId = function(data, status, req) {
    var location = req.getResponseHeader('Location');
    var parts = location.split("/");
    var sessionID = parts[parts.length - 1];
    sessionId = sessionID;
    withValidSessionId();
  };
  $.post(API_BASE + '/diffs/sessions', {}, handleSessionId, "json");
}

var startTime, endTime;
var bucketSize = 3600;

const TIME_FORMAT = "yyyyMMddTHHmmssZ";
var swimlaneLabels = [];

function nearestHour() {
  var hours = (new Date()).getHours() + 1;
  return Date.today().add({hours: hours});
}
function loadBuckets() {
  endTime = nearestHour();

  var now = endTime.toString(TIME_FORMAT);

  startTime = endTime.add({seconds: -1 * bucketSize * maxColumns});
  var dayBeforeNow = startTime.toString(TIME_FORMAT);

  $.get("rest/diffs/sessions/" + sessionId + "/zoom?range-start=" + dayBeforeNow + "&range-end=" + now + "&bucketing=" + bucketSize, function(data) {
    // update swimlane labels
    var i = 0;
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
        for (var j = 0; j < maxColumns; j++)
          buckets[i][j] = values[j] || 0;
      } else {
        // if a pair wasn't in the results, initialize or keep existing data
        if (! buckets[i]) {
          buckets[i] = [];
          for (var j = 0; j < maxColumns; j++)
            buckets[i][j] = 0;
        }
      }
    }
    
    clearEverything();
    recalibrateHeatmap();
    o_x = -1 * rightLimit;
    context.translate(o_x, o_y);
    scaleContext.translate(o_x, o_y);
    drawGrid();
  });
}

function renderEntityScopedActions(pairKey, itemID) {
  var actionListContainer = $("#actionlist").empty();
  var actionListCallback = function(actionList, status, xhr) {
    if (!actionList) {
      return;
    }
    $.each(actionList, function(i, action) {
      var repairStatus = $('#repairstatus');
      appendActionButtonToContainer(actionListContainer, action, pairKey, itemID, repairStatus);
    });
  };

  $.ajax({ url: API_BASE + '/actions/' + pairKey + '?scope=entity', success: actionListCallback });
}

/**
 * Renders a difference event in the content viewer panel
 * @param event
 */
function renderEvent(event) {
  if (event == null) return;

  var itemID = event.objId.id,
      pairKey = event.objId.pairKey,
      seqID = event.seqId,
      upstreamLabel = "upstream",
      upstreamVersion = event.upstreamVsn || "no version",
      downstreamLabel = "downstream",
      downstreamVersion = event.downstreamVsn || "no version";

  $('#content-label').text('Content for item ID: ' + itemID);

  $('#item1 .upstreamLabel').text(upstreamLabel);
  $('#item1 .diff-hash').text(upstreamVersion);

  $('#item2 .downstreamLabel').text(downstreamLabel);
  $('#item2 .diff-hash').text(downstreamVersion);

  var getContent = function(selector, label, upOrDown, pendingRequest) {

    if (pendingRequest) {
      pendingRequest.abort();
    }

    $(selector).hide();
    var busy = $(selector).prev();
    busy.show();

    pendingRequest = $.ajax({
          url: "rest/diffs/events/" + sessionId + "/" + seqID + "/" + upOrDown,
          success: function(data) {
            $(selector).text(data || "no content found for " + upOrDown);
          },
          complete: function(x,status) {
            // If the reason for completion is an abort, then leave the busy spinner in focus
            // otherwise, fade it out and let the element get rendered again
            if (status != "abort") {
              pendingRequest = undefined;
              busy.fadeOut('fast');
              $(selector).show();
            }
          },
          error: function(xhr, status, ex) {
            if (status != "abort" && console && console.log) {
              console.log('error getting the content for ' + (label || "(no label)"), status, ex, xhr);
            }
          }
        });
    return pendingRequest;
  };

  $.get("rest/config/pairs/" + pairKey, function(data, status, xhr) {
    upstreamLabel = data.upstream.name;
    $("#item1 h6").text(upstreamLabel);
    downstreamLabel = data.downstream.name;
    $("#item2 h6").text(downstreamLabel);
    pendingUpstreamRequest = getContent("#item1 pre", upstreamLabel, "upstream", pendingUpstreamRequest);
    pendingDownstreamRequest = getContent("#item2 pre", downstreamLabel, "downstream", pendingDownstreamRequest);
  });

  renderEntityScopedActions(pairKey, itemID);
}

function selectFromList(event) {
  if (!event) {
    return false;
  }

  if (event.target.parentNode.id === "difflist-header") {
    return false;
  }

  // TODO This selector is a real hack
  var row = $(event.target).closest('div[id*="evt_"]');
  var event = row.data("event");
  if (event != null) {
    renderEvent(event);
    $('#diffList').find('div').removeClass("specific_selected");
    $('#evt_' + row.data("event").seqId).addClass("specific_selected");
  }
}

/**
 * Appends a row to the difflist table
 * @param table
 * @param event
 */
function addRow(table, event) {
  var time = new Date(event.detectedAt).toString("HH:mm:ss");
  var date = new Date(event.detectedAt).toString("dd/MM/yyyy");
  var row = $("<div class='span-14' id='evt_" + event.seqId + "'></div>")
      .append("<div class='span-2'>" + date + "</div>")
      .append("<div class='span-2'>" + time + "</div>")
      .append("<div class='span-3 wrappable'>" + event.objId.pairKey + "</div>")
      .append("<div class='span-3 wrappable'>" + event.objId.id + "</div>")
      .data("event", event);

  if (!event.upstreamVsn) {
    row.append("<div class='span-4 last'>Missing from upstream</div>");
  }
  else if (!event.downstreamVsn) {
    row.append("<div class='span-4 last'>Missing from downstream</div>");
  }
  else {
    row.append("<div class='span-4 last'>Data difference</div>");
  }

  table.append(row);
}

var listSize = 20;
var page = 0;
function previous() {
  if (page > 0) {
    page--;
  }
  fetchData();
}

var itemCount = 0;
function next() {
  if (selectedBucket != null && buckets[selectedBucket.row] != null) {
    if ((page + 1) * listSize < itemCount) {
      page++;
      fetchData();
    }
  }
}

function fetchData() {
  itemCount = 0;
  if (selectedBucket != null && buckets[selectedBucket.row] != null) {
    if (buckets[selectedBucket.row][selectedBucket.column] > 0) {
      for (var i = 0; i < maxRows; i++) {
        itemCount += buckets[i][selectedBucket.column];
      }
      var selectedStart = new Date(startTime.getTime() + (selectedBucket.column * bucketSize * 1000));
      var selectedEnd = new Date(selectedStart.getTime() + (bucketSize * 1000));

      var pairKey = swimlaneLabels[selectedBucket.row];

      var url = "rest/diffs/sessions/" + sessionId + "?pairKey=" + pairKey + "&range-start="
          + selectedStart.toString(TIME_FORMAT) + "&range-end=" + selectedEnd.toString(TIME_FORMAT)
          + "&offset=" + (page * listSize) + "&length=" + listSize;

      $.get(url, function(data) {
        renderEvent(data[0]);
        var list = $('#difflist-row').empty();
        $.each(data, function(i, event) {
          addRow(list, event);
        });
      });
      $("#pagecount").text("Page " + (page + 1) + " of " + Math.ceil(itemCount / listSize));
      $("#navigation").show();
    }
  }
}

var timeout;
var polling = true;
function startPolling() {
  polling = true;
  clearTimeout(timeout);
  loadBuckets();
  timeout = window.setTimeout(startPolling, 5000);
  $("#polling").text("Stop polling");
}

function stopPolling() {
  polling = false;
  clearTimeout(timeout);
  $("#polling").text("Start polling");
}

function dashedLine(ctx, x1, y1, x2, y2, dashLen) {
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
}

function drawCircle(i, j) {
  var cell = coordsToCell({"x":i, "y":j});
  if (cell.column < maxColumns && cell.row < maxRows) {
    var cell_x = i + Math.floor(gridSize / 2);
    var cell_y = j + gutterSize + Math.floor(gridSize / 2);
    var size = limit(buckets[cell.row][cell.column], Math.floor((gridSize - 1) / 2));
    if (size.value > 0) {
      // if the size has been limited, draw the outline slightly thicker
      context.lineWidth = size.limited ? 2 : 1;
      context.strokeStyle = colours.black;
      context.fillStyle = colours.white;
      context.beginPath();
      context.arc(cell_x, cell_y, size.value, 0, Math.PI * 2, false);
      context.closePath();
      context.stroke();
      context.fill();
    }
  }
}

function drawArrow(ctx, dir, x, y, w, h) {
  var headWidth = w / 2;
  var cornerHeight = h - (h / 4);

  var startX = x + (dir == directions.left ? 0 : w),
      headX  = x + (dir == directions.left ? headWidth : w - headWidth),
      endX   = x + (dir == directions.left ? w : 0);

  var gradient = context.createLinearGradient(startX, y, endX, y);
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
}

/**
 * Finds a cell with a fully- or partially-visible blob at the given coordinates.
 * The "dir" parameter controls whether blob visibility is determined with respect
 * to the left or right of the x position.
 */
function findCellWithVisibleBlob(x, y, dir) {
  var cell = coordsToCell({"x": x, "y": y});
  var radius = limit(buckets[cell.row][cell.column], Math.floor((gridSize - 1) / 2));
  if (radius.value > 0) {
    var cutoff = cellToCoords(cell).x + (gridSize / 2) + (dir == directions.left ? radius.value : -1 * radius.value);
    if (dir == directions.left && x > cutoff) {
      // nudge to the right if the leftmost cell's blob is no longer visible
      cell.column++;
    } else if (dir == directions.right && x < cutoff) {
      // nudge to the left if the rightmost cell's blob is no longer visible
      cell.column--;
    }
  }
  return cell;
}

function nonEmptyCellExists(row, startColumn, endColumn) {
  var cols = buckets[row];
  for (var i = startColumn; i < endColumn; i++) {
    if (cols[i] > 0)
      return true;
  }
  return false;
}

var toggleX, toggleY;
var show_grid = false;
function drawGrid() {
  var region_width = maxColumns * gridSize;
  // draw grid lines
  if (show_grid) {
    for (var x = 0.5; x < region_width; x += gridSize) {
      context.moveTo(x, 0);
      context.lineTo(x, canvas.height);
    }
    for (var y = 0.5; y < canvas.height; y += (2 * gutterSize + gridSize)) {
      context.moveTo(0, y);
      context.lineTo(region_width, y);
    }
    context.strokeStyle = colours.red;
    context.stroke();
  }

  // draw swim lanes
  var lane = 0;
  var laneHeight = swimlaneHeight();
  var arrowWidth = 18;
  var arrowHeight = 12;
  var viewportX = o_x;
  viewportX = Math.abs(viewportX);// workaround for a bug in Chrome, Math.abs sometimes gets optimized away or otherwise borked
  for (var s = 0.5 + laneHeight; s < canvas.height; s += laneHeight) {
    dashedLine(underlayContext, 0, s, canvas.width, s, 2);
    if (swimlaneLabels[lane] != null) {
      underlayContext.font = "11px 'Lucida Grande', Tahoma, Arial, Verdana, sans-serif";
      underlayContext.fillStyle = colours.black;
      underlayContext.fillText(swimlaneLabels[lane], 10, s - laneHeight + arrowHeight);
    }
    var leftCell = findCellWithVisibleBlob(viewportX, s - laneHeight, directions.left);
    if (nonEmptyCellExists(leftCell.row, 0, leftCell.column)) {
      drawArrow(underlayContext, directions.left, 10, s - (arrowHeight / 4) - (gridSize / 2), arrowWidth, arrowHeight);
    }
    var rightCell = findCellWithVisibleBlob(viewportX + canvas.width - 1, s - laneHeight, directions.right);
    if (nonEmptyCellExists(rightCell.row, rightCell.column + 1, maxColumns)) {
      drawArrow(underlayContext, directions.right, canvas.width - 10 - arrowWidth, s - (arrowHeight / 4) - (gridSize / 2), arrowWidth, arrowHeight);
    }
    lane++;
  }

  // draw "live" / "click to poll" text
  drawLiveIndicator()

  // draw circles
  for (var i = 0.5; i < region_width; i += gridSize) {
    for (var j = 0.5; j < canvas.height; j += (2 * gutterSize + gridSize)) {
      drawCircle(i, j);
    }
  }

  // draw scale
  scaleContext.font = "9px sans-serif";
  for (var sc = 0; sc < maxColumns; sc++) {
    if (sc % 3 == 0) {
      var tick = new Date(startTime.getTime() + (sc * bucketSize * 1000));
      scaleContext.fillText(tick.toString("dd/MM"), sc * gridSize, 10);
      scaleContext.fillText(tick.toString("HH:mm"), sc * gridSize, 20);
    }
  }
}

var highlighted;
function drawOverlay() {
  if (highlighted != null && highlighted.column >= 0 && highlighted.row >= 0) {
    var value = buckets[highlighted.row][highlighted.column];
    if (value > 0) {
      var c_x = highlighted.column * gridSize;
      var c_y = (highlighted.row * (2 * gutterSize + gridSize)) + gutterSize + gridSize;
      overlayContext.font = "12px sans-serif";
      overlayContext.textBaseline = "top";
      var width = context.measureText("" + value).width;
      overlayContext.fillText(value, c_x + Math.floor(gridSize / 2) - Math.floor(width / 2), c_y);
    }
  }
}

/**
 * Limits a value to a given maximum value, somewhat like Math.min().
 * Returns an object with two properties: "value", the limited value and "limited",
 * which flags whether the original value was greater than the maximum value.
 */
function limit(value, maximum) {
  if (value <= maximum) {
    return {"value":value, "limited":false};
  }
  return {"value":maximum, "limited":true};
}

var o_x = rightLimit;
var o_y = 0;
function coords(e) {
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

  x -= heatmap.offsetLeft;
  y -= heatmap.offsetTop;

  return { "x":x, "y":y };
}

function coordsToCell(coords) {
  return {
    "row": Math.floor(coords.y / (2 * gutterSize + gridSize)),
    "column": Math.floor((coords.x) / gridSize)
  };
}

function cellToCoords(cell) {
  return {
    "x": cell.column * gridSize,
    "y": cell.row * (2 * gutterSize + gridSize)
  };
}

function clearEverything() {
  clearCanvas();
  clearOverlay();
  clearUnderlay();
  clearScale();
}

var dragging = false;
var dragged = false;
function mouseDown(e) {
  dragging = e;
  dragged = false;
  e.target.style.cursor = "move";
  return false;
}

function togglePolling(c) {
  if (c.x > toggleX && c.y < toggleY) {
    if (polling) {
      stopPolling();
    }
    else {
      startPolling();
    }
  }
}

function mouseUp(e) {
  dragging = false;
  if (!dragged) {
    if (e.target.tagName == "CANVAS") {
      var c = coords(e);
      togglePolling(c);
      c.x -= o_x;
      selectedBucket = coordsToCell(c);
      page = 0;
      fetchData();
    }
  } else {
    if (Math.abs(o_x) >= rightLimit) {
      if (!polling) startPolling();
    }
  }
  dragged = false;
  e.target.style.cursor = "default";
}

function mouseMove(e) {
  if (dragging) {
    stopPolling();
    dragged = true;
    clearEverything();
    var m_coords = coords(e);
    var d_coords = coords(dragging);
    o_x += m_coords.x - d_coords.x;
    if (o_x > 0) {
      o_x = 0;
    }

    if (Math.abs(o_x) > rightLimit) {
      o_x = -1 * rightLimit;
    }
    context.translate(o_x, o_y);
    scaleContext.translate(o_x, 0);
    drawGrid();
    dragging = e;
  }
  else {
    clearOverlay();
    overlayContext.translate(o_x, o_y);
    mouseOver(e);
  }
}

function mouseOver(e) {
  var c = coords(e);
  c.x -= o_x;
  var cell = coordsToCell(c);
  if (cell.row >= 0 && cell.row < maxRows && cell.column >= 0 && cell.column < maxColumns) {
    highlighted = cell;
    drawOverlay();
  }
}

function updateZoomControls() {
  function toggleControl(selector, isDisabled) {
    if (isDisabled) {
      $(selector).attr('disabled', 'disabled');
    } else {
      $(selector).removeAttr('disabled');
    }
  }

  toggleControl('#zoomIn', !shouldAllowMoreZoomIn());
  toggleControl('#zoomOut', !shouldAllowMoreZoomOut());
}

function shouldAllowMoreZoomIn() {
  return bucketSize > 1;      // Buckets can't be smaller than 1s
}
function shouldAllowMoreZoomOut() {
  return bucketSize < 180*24*3600;  // Buckets can't be wider than 6-months
}

function zoomOut() {
  bucketSize = bucketSize * 2;
  updateZoomControls();

  startPolling();
}
function zoomIn() {
  bucketSize = Math.round(bucketSize / 2);
  updateZoomControls();
  
  startPolling();
}

function initGraph() {
  createSession(startPolling);
  initCanvas();

  $(document).mouseup(mouseUp);
  $(document).mousemove(mouseMove);

  // Register the handling for dragging the heatmap on the highest layer
  overlay.onmousedown = mouseDown;

  $("#heatmap").bind("contextmenu", function(e) {
    return false;
  });
  $("#diffList").click(function(e) {
    selectFromList(e);
  });

  $("#next").click(function(e) {
    next();
  });

  $("#previous").click(function(e) {
    previous();
  });
  $("#zoomOut").click(function(e) {
    zoomOut();
  });
  $(document).keypress(function(e) {
    if (e.charCode == '+'.charCodeAt()) {
      e.preventDefault();
      if (shouldAllowMoreZoomIn()) zoomIn();
    }
    if (e.charCode == '-'.charCodeAt()) {
      e.preventDefault();
      if (shouldAllowMoreZoomOut()) zoomOut();
    }

    return true;
  });
  $("#zoomIn").click(function(e) {
    zoomIn();
  });

  // Don't leave the buttons focussed
  $('#zoomIn').focus(function(e) {
    $(this).blur();
  });
  $('#zoomOut').focus(function(e) {
    $(this).blur();
  });

  $("#navigation").hide();

  pulseUpLive();
}

var livePulseTracker = $.extend($('<div>')[0], {
  backgroundColor: colours.red,

  customAnimate: true,
  updated: true
});
livePulseTracker.style.backgroundColor = colours.red;

var liveIndicatorActiveColour = colours.red;
function drawLiveIndicator() {
  var pollText = polling ? "LIVE" : "PAUSED";
  var textWidth = underlayContext.measureText(pollText).width;
  var textSpacer = 20;
  underlayContext.fillStyle = polling ? liveIndicatorActiveColour : colours.yellow;
  underlayContext.fillRect(canvas.width - textWidth - textSpacer, 0, textWidth + textSpacer, 20);
  underlayContext.fillStyle = polling ? colours.white : colours.black;
  underlayContext.font = "12px 'Lucida Grande', Tahoma, Arial, Verdana, sans-serif";
  underlayContext.textBaseline = "top";
  underlayContext.fillText(pollText, canvas.width - underlayContext.measureText(pollText).width - (textSpacer / 2), 5);
  toggleX = canvas.width - textWidth - textSpacer;
  toggleY = 20;
}

function pulseLiveToColour(colour, completionFn) {
  $(livePulseTracker).animate({ backgroundColor: colour }, {
    duration: 2000,
    easing: 'linear',
    step: function(now, fx) {
      liveIndicatorActiveColour = fx.elem.style.backgroundColor;
      drawLiveIndicator();
    },
    complete: function() {
      completionFn();
    }
  });
}

function pulseUpLive() {
  pulseLiveToColour(colours.fadedRed, pulseDownLive);
}
function pulseDownLive() {
  pulseLiveToColour(colours.red, pulseUpLive);
}