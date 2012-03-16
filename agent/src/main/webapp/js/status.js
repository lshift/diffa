
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

Diffa.Routers.Pairs = Backbone.Router.extend({
  initialize: function(opts) {
    var self = this;

    this.domain = opts.domain;

    $(opts.el).on(this.domain.id + ':pairSelected', function(e, selectedPairId) {
      self.navigate("pair/" + selectedPairId, true);
    });
  },

  routes: {
    "":                  "index",      // #
    "pair/:pair":        "managePair"  // #pair/WEB-1
  },

  index: function() {
  },

  managePair: function(pairKey) {
    this.domain.pairStates.select(pairKey);
  }
});

Diffa.Views.PairList = Backbone.View.extend({
  initialize: function() {
    _.bindAll(this, "addPair", "removePair");

    this.model.bind('add', this.addPair);
    this.model.bind('remove', this.removePair);

    this.model.watch(this.el);

    $(this.el).html(window.JST['status/pairlist']());
  },

  addPair: function(pair) {
    var view = new Diffa.Views.PairSelector({model: pair});
    $(this.el).append(view.render().el);
  },

  removePair: function(pair) {
    pair.remove();
  }
});

Diffa.Views.PairSelector = Backbone.View.extend({
  tagName: "div",
  className: "pair",
  template: window.JST['status/pairselector-item'],

  events: {
    "click":  "select"
  },

  initialize: function() {
    _.bindAll(this, "render", "select", "close");

    this.model.bind('change:state',     this.render);
    this.model.bind('change:selected',  this.render);
    this.model.bind('remove',           this.close);

    this.domain = this.model.collection.domain;   // Make access to the domain cleaner
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
    $(this.el).trigger(this.domain.id  + ':pairSelected', [this.model.id]);
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

Diffa.Views.PairSelectionView = Backbone.View.extend({
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

Diffa.Views.PairActions = Diffa.Views.PairSelectionView.extend({
  initialize: function() {
    Diffa.Views.PairSelectionView.prototype.initialize.call(this);

    $(this.el).html(window.JST['status/pairactions']());

    new Diffa.Views.PairControls({el: this.$('.pair-controls'), model: this.model});
    new Diffa.Views.PairRepairs({el: this.$('.pair-repairs'), model: this.model});
    new Diffa.Views.PairReports({el: this.$('.pair-reports'), model: this.model});

    this.model.bind('change:selected',    this.maybeRender);
  },
  render: function() {
    $(this.el).show();
  }
});

Diffa.Views.PairControls = Diffa.Views.PairSelectionView.extend({
  events: {
    "click  .scan-button":       "startScan",
    "click  .cancel-button":     "cancelScan"
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

    var pairScanButton = this.$('.pair-scan-button');
    var scanButtons = this.$('.scan-button');
    var cancelButton = this.$('.cancel-button');

    $(cancelButton).toggle(scanIsRunning);

    this.$('.view-scan-button').remove();
    if (currentPair.get('fullContent')) {
      // The presence of 'fullContent' indicates that the full data has been loaded
      var views = _.sortBy(currentPair.get('views'), function(v) { return v.name; });

      // Render scan buttons only if we're not currently doing a scan
      if (!scanIsRunning) {
        _.each(views, function(view) {
          $('<button class="repair scan-button view-scan-button">Scan ' + view.name + '</button>').
            appendTo(self.el).
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

    $(this.el).show();
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
  initialize: function() {
    Diffa.Views.PairSelectionView.prototype.initialize.call(this);

    _.bindAll(this, "render");

    this.model.bind('change:selected', this.maybeRender);
    this.model.bind('change:' + this.controlSet,  this.maybeRender);

    this.maybeRender();
  },

  render: function() {
    var self = this;

    this.$('button').remove();

    var currentPair = self.model.get(self.currentPairKey);
    var currentControls = currentPair.get(self.controlSet);

    // Only show the loading flower if we don't have items loaded
    this.$('.loading').toggle(currentControls == null);

    if (currentControls != null)
      this.renderPanel(currentPair, currentControls);

    // Only show ourselves if we're still loading or have content
    $(this.el).toggle(currentControls == null || currentControls.length > 0);
  }
});

Diffa.Views.PairRepairs = Diffa.Views.PairControlSet.extend({
  controlSet: 'actions',

  initialize: function() {
    Diffa.Views.PairControlSet.prototype.initialize.call(this);
  },

  renderPanel: function(currentPair, currentActions) {
    var self = this;

    _.each(currentActions, function(action) {
      appendActionButtonToContainer(self.el, action, self.model.get(self.currentPairKey), null, null);
    });
  }
});

Diffa.Views.PairReports = Diffa.Views.PairControlSet.extend({
  controlSet: 'reports',

  initialize: function() {
    Diffa.Views.PairControlSet.prototype.initialize.call(this);
  },

  renderPanel: function(currentPair, currentReports) {
    var self = this;

    _.each(currentReports, function(report) {
      $('<button class="repair">Run ' + report.name +  ' Report</button>').
        appendTo(self.el).
        click(function(e) {
          e.preventDefault();
          currentPair.runReport(report.name);
        });
      });
  }
});

Diffa.Views.PairLog = Diffa.Views.PairSelectionView.extend({
  initialize: function() {
    Diffa.Views.PairSelectionView.prototype.initialize.call(this);

    $(this.el).html(window.JST['status/pairlog']());

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

        $(self.el).append('<div class="entry entry-' + entry.level.toLowerCase() + '">' + text + '</div>')
      });
    }
    $(this.el).show();
  }
});

$('.diffa-pair-status-list').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.PairList({el: this, model: domain.pairStates});
});
$('.diffa-pair-actions').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.PairActions({el: this, model: domain.pairStates});
});
$('.diffa-pair-log').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.PairLog({el: this, model: domain.pairStates});
});

$('.diffa-status-page').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Routers.Pairs({domain: domain, el: this});
  Backbone.history.start();
});
});
