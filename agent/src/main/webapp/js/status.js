
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

var Diffa = {
  Routers: {},
  Views: {},
  Collections: {},
  Models: {},
  Config: {
    LogPollInterval:    2000,     // How frequently (in ms) we poll for log updates
    PairStateInterval:  5000      // How frequently (in ms) we poll for pair state updates
  }
};

$(function() {

Diffa.Routers.Pairs = Backbone.Router.extend({
  routes: {
    "":                  "index",      // #
    "pair/:pair":        "managePair"  // #pair/WEB-1
  },

  index: function() {
  },

  managePair: function(pairKey) {
    Diffa.PairsCollection.select(pairKey);
  }
});

Diffa.Models.Pair = Backbone.Model.extend({
  initialize: function() {
    var self = this;

    _.bindAll(this, "remove", "syncLog", "startScan", "cancelScan");

    // If we become selected, then we should fetch our actions
    this.bind("change:selected", function(pair) {
      if (pair.get('selected')) {
        self.fetchActions();
        self.fetchReports();
        self.fetchFullDetails();

        self.logPollIntervalId = window.setInterval(self.syncLog, Diffa.Config.LogPollInterval);
        self.syncLog();
      } else {
        if (self.logPollIntervalId) {
          window.clearInterval(self.logPollIntervalId);
          delete self.logPollIntervalId;
        }
      }
    })
  },

  remove: function() {
    this.trigger('remove');
  },

  fetchActions: function() {
    var self = this;
    $.getJSON(API_BASE + "/domains/" + Diffa.currentDomain + '/actions/' + this.id + '?scope=pair', function(actions) {
      self.set({actions: actions});
    });
  },

  fetchReports: function() {
    var self = this;
    $.getJSON(API_BASE + "/domains/" + Diffa.currentDomain + '/reports/' + this.id, function(reports) {
      self.set({reports: reports});
    });
  },

  fetchFullDetails: function() {
    var self = this;
    $.getJSON(API_BASE + "/domains/" + Diffa.currentDomain + '/config/pairs/' + this.id, function(pairInfo) {
      self.set(pairInfo);
      self.set({fullContent: true})
    });
  },

  syncLog: function() {
    var self = this;
    $.getJSON(API_BASE + "/domains/" + Diffa.currentDomain + "/diagnostics/" + this.id + "/log", function(logEntries) {
      self.set({logEntries: logEntries});
    });
  },

  startScan: function(view) {
    var self = this;
    var data = {};
    if (view) {
      data.view = view;
    }
    
    this.set({state: 'REQUESTING'});
    $.ajax({
      url: API_BASE + "/domains/" + Diffa.currentDomain + "/scanning/pairs/" + this.id + "/scan",
      type: "POST",
      data: data,
      success: function() {
        self.set({state: 'SCANNING'});
      },
      error: function(jqXHR, textStatus, errorThrown) {
        alert("Error in scan request: " + errorThrown);
      }
    });
  },

  cancelScan: function() {
    var self = this;

    this.set({state: 'CANCELLED'});
    $.ajax({
      url: API_BASE + "/domains/" + Diffa.currentDomain + "/scanning/pairs/" + this.id + "/scan",
      type: "DELETE",
      success: function() {
      },
      error: function(jqXHR, textStatus, errorThrown) {
        alert("Error in scan cancellation request: " + errorThrown);
      }
    });
  },

  runReport: function(name) {
    var self = this;
    
    $.ajax({
      url: API_BASE + "/domains/" + Diffa.currentDomain + "/reports/" + this.id + "/" + name,
      type: "POST",
      error: function(jqXHR, textStatus, errorThrown) {
        alert("Error in report request: " + errorThrown);
      }
    });
  }
});

Diffa.Collections.Pairs = Backbone.Collection.extend({
  model: Diffa.Models.Pair,
  url: function() { return API_BASE + "/domains/" + Diffa.currentDomain + "/scanning/states"; },

  initialize: function() {
    _.bindAll(this, "sync", "scanAll", "select");

    this.bind("add", function(pair) {
      pair.set({selected: pair.id == this.selectedPair});
    });
  },

  sync: function() {
    var self = this;

    $.getJSON(this.url(), function(states) {
      var toRemove = [];

      // Update any pairs we've already got
      self.each(function(currentPair) {
        var newState = states[currentPair.id];

        if (newState) {
          // We're updating an existing pair
          currentPair.set({state: newState});
        } else {
          toRemove.push(currentPair);
        }
      });

      // Remove removable pairs
      _.each(toRemove, function(r) { self.remove(r); });

      // Add any pairs we haven't seen
      for (var key in states) {
        if (!self.get(key)) {
          self.add([{id: key, state: states[key]}]);
        }
      }
    });
  },

  scanAll: function() {
    var self = this;

    this.each(function(pair) {
      pair.set({state: 'REQUESTING'});
    });

    $.ajax({
      url: API_BASE + "/domains/" + Diffa.currentDomain + "/scanning/scan_all",
      type: "POST",
      success: function() {
        self.each(function(pair) {
          pair.set({state: 'SCANNING'});
        });
      },
      error: function(jqXHR, textStatus, errorThrown) {
        alert("Error in scan request: " + errorThrown);
      }
    });
  },

  select: function(pairKey) {
    this.selectedPair = pairKey;
    this.each(function(pair) {
      pair.set({selected: pair.id == pairKey});
    });
  }
});

Diffa.Views.PairList = Backbone.View.extend({
  el: $('#pair-list'),

  initialize: function() {
    _.bindAll(this, "addPair", "removePair");

    this.model.bind('add', this.addPair);
    this.model.bind('remove', this.removePair);
  },

  addPair: function(pair) {
    var view = new Diffa.Views.PairSelector({model: pair});
    this.el.append(view.render().el);
  },

  removePair: function(pair) {
    pair.remove();
  }
});

Diffa.Views.PairSelector = Backbone.View.extend({
  tagName: "div",
  className: "pair",
  template: _.template($('#pair-selector-template').html()),

  events: {
    "click":  "select"
  },

  initialize: function() {
    _.bindAll(this, "render", "select", "close");

    this.model.bind('change:state',     this.render);
    this.model.bind('change:selected',  this.render);
    this.model.bind('remove',           this.close);
  },

  render: function() {
    $(this.el).html(this.template({name: this.model.id, state: this.renderState(this.model.get('state'))}));
    $(this.el).attr('pair-key', this.model.id);
    $(this.el).toggleClass('selected-pair', this.model.get('selected'));
    return this;
  },

  close: function() {
    $(this.el).remove();
  },

  select: function() {
    Diffa.SettingsApp.navigate("pair/" + this.model.id, true);
  },

  renderState: function(state) {
    switch (state) {
      case "REQUESTING":    return "Requesting Scan";
      case "UNKNOWN":       return "Scan not run";
      case "FAILED":        return "Last Scan Failed";
      case "UP_TO_DATE":    return "Up to Date";
      case "SCANNING":      return "Scan In Progress";
      case "CANCELLED":     return "Last Scan Cancelled";
    }

    return null;
  }
});

// Base class inherited by views that take a pair collection and then find the selected item to display it.
Diffa.Views.PairSelectionView = Backbone.View.extend({
  el: $('#pair-actions'),

  initialize: function() {
    _.bindAll(this, "maybeRender");
  },

  maybeRender: function(pair) {
    // We can sometimes be invoked without a pair. In that case, scan the collection to find a
    if (pair == null) {
      pair = this.model.detect(function(pair) { return pair.get('selected'); });

      // If nothing is selected, then we have nothing to do.
      if (pair == null) return;
    }

    if (!pair.get('selected') && pair.id == this.currentPairKey) {
      // The pair we've previously been showing is no longer visible. Hide our view.
      $(this.el).hide();
    } else if (pair.get('selected')) {
      this.currentPairKey = pair.id;
      this.render();
    }
  }
});

Diffa.Views.PairControls = Diffa.Views.PairSelectionView.extend({
  el: $('#pair-actions'),

  events: {
    "click  #pair-controls .scan-button":       "startScan",
    "click  #pair-controls .cancel-button":     "cancelScan"
  },

  initialize: function() {
    Diffa.Views.PairSelectionView.prototype.initialize.call(this);

    _.bindAll(this, "render");

    this.model.bind('change:selected',    this.maybeRender);
    this.model.bind('change:state',       this.maybeRender);
    this.model.bind('change:fullContent', this.maybeRender);

    this.maybeRender();
  },

  render: function() {
    var self = this;
    var currentPair = self.model.get(self.currentPairKey);
    var currentState = currentPair.get('state');
    var scanIsRunning = (currentState == "REQUESTING" || currentState == "SCANNING");

    var pairScanButton = this.$('#pair-controls .pair-scan-button');
    var scanButtons = this.$('#pair-controls .scan-button');
    var cancelButton = this.$('#pair-controls .cancel-button');

    $(cancelButton).toggle(scanIsRunning);

    this.$('#pair-controls .view-scan-button').remove();
    if (currentPair.get('fullContent')) {
      // The presence of 'fullContent' indicates that the full data has been loaded
      var views = _.sortBy(currentPair.get('views'), function(v) { return v.name; });

      // Render scan buttons only if we're not currently doing a scan
      if (!scanIsRunning) {
        _.each(views, function(view) {
          $('<button class="repair scan-button view-scan-button">Scan ' + view.name + '</button>').
            appendTo($('#pair-controls')).
            click(function(e) {
              e.preventDefault();
              currentPair.startScan(view.name);
            });
        });
      }

      // Only show the pair scan button if we're not scanning and we allow manual scans
      var allowManualScans = (currentPair.get('allowManualScans') !== false);
      $(pairScanButton).toggle(!scanIsRunning && allowManualScans);
    } else {
      $(scanButtons).hide();      // Don't show scan buttons till we know if they're allowed
    }

    this.el.show();
  },

  startScan: function() {
    var currentPair = this.model.get(this.currentPairKey);
    currentPair.startScan();
  },

  cancelScan: function() {
    var currentPair = this.model.get(this.currentPairKey);
    currentPair.cancelScan();
  }
});

Diffa.Views.PairControlSet = Diffa.Views.PairSelectionView.extend({
  el: $('#pair-actions'),

  initialize: function() {
    Diffa.Views.PairSelectionView.prototype.initialize.call(this);

    _.bindAll(this, "render");

    this.model.bind('change:selected', this.maybeRender);
    this.model.bind('change:' + this.controlSet,  this.maybeRender);

    this.maybeRender();
  },

  render: function() {
    var self = this;

    $(this.panelEl).find('button').remove();

    var currentPair = self.model.get(self.currentPairKey);
    var currentControls = currentPair.get(self.controlSet);

    // Only show the loading flower if we don't have items loaded
    $(this.panelEl).find('.loading').toggle(currentControls == null);

    if (currentControls != null)
      this.renderPanel(currentPair, currentControls);

    // Only show ourselves if we're still loading or have content
    $(this.panelEl).toggle(currentControls == null || currentControls.length > 0);
  }
});

Diffa.Views.PairRepairs = Diffa.Views.PairControlSet.extend({
  panelEl: $('#pair-repairs'),
  controlSet: 'actions',

  renderPanel: function(currentPair, currentActions) {
    var self = this;

    _.each(currentActions, function(action) {
      appendActionButtonToContainer(self.panelEl, action, self.model.get(self.currentPairKey), null, null);
    });
  }
});

Diffa.Views.PairReports = Diffa.Views.PairControlSet.extend({
  panelEl: $('#pair-reports'),
  controlSet: 'reports',

  renderPanel: function(currentPair, currentReports) {
    var self = this;

    _.each(currentReports, function(report) {
      $('<button class="repair">Run ' + report.name +  ' Report</button>').
        appendTo(self.panelEl).
        click(function(e) {
          e.preventDefault();
          currentPair.runReport(report.name);
        });
      });
  }
});

Diffa.Views.PairLog = Diffa.Views.PairSelectionView.extend({
  el: $('#pair-log'),

  initialize: function() {
    Diffa.Views.PairSelectionView.prototype.initialize.call(this);

    _.bindAll(this, "render");

    this.model.bind('change:selected',    this.maybeRender);
    this.model.bind('change:logEntries',  this.maybeRender);

    this.maybeRender();
  },

  render: function() {
    var self = this;

    this.$('div.entry').remove();

    var currentPair = self.model.get(self.currentPairKey);
    var currentLogEntries = currentPair.get('logEntries');

    // Only show the loading flower if we don't have log entries loaded
    this.$('.loading').toggle(currentLogEntries == null);

    if (currentLogEntries != null) {
      _.each(currentLogEntries, function(entry) {
        var time = new Date(entry.timestamp).toString("dd/MM/yyyy HH:mm:ss");
        var text = '<span class="timestamp">' + time + '</span><span class="msg">' + entry.msg + '</span>';

        self.el.append('<div class="entry entry-' + entry.level.toLowerCase() + '">' + text + '</div>')
      });
    }
    this.el.show();
  }
});

$('#scan_all').click(function(e) {
  Diffa.PairsCollection.scanAll();
});

Diffa.currentDomain = "diffa";    // TODO: Allow user to change this
Diffa.SettingsApp = new Diffa.Routers.Pairs();
Diffa.PairsCollection = new Diffa.Collections.Pairs();
Diffa.PairListView = new Diffa.Views.PairList({model: Diffa.PairsCollection});
Diffa.PairControlsView =  new Diffa.Views.PairControls({model: Diffa.PairsCollection});
Diffa.PairRepairsView =  new Diffa.Views.PairRepairs({model: Diffa.PairsCollection});
Diffa.PairReportsView =  new Diffa.Views.PairReports({model: Diffa.PairsCollection});
Diffa.PairLogView =  new Diffa.Views.PairLog({model: Diffa.PairsCollection});
Backbone.history.start();

Diffa.PairsCollection.sync();
setInterval('Diffa.PairsCollection.sync()', Diffa.Config.PairStateInterval);
  
});
