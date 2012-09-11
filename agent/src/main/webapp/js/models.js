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

Diffa.Helpers.ViewsHelper = {
  extractViews: function(model, viewCollectionClass) {
    var updateViews = function() {
      model.views.reset(model.get('views'));
    };

    model.bind('change:views', updateViews);
    model.views = new (viewCollectionClass || Backbone.Collection)([]);
    Diffa.Helpers.DirtyTracker.proxyChange(model, [model.views]);
    updateViews();
  },
  packViews: function(model) {
    model.views.each(function(m) { if (m.prepareForSave) m.prepareForSave(); });
    model.set({views: model.views.toJSON()}, {silent: true});
  }
};

Diffa.Helpers.DatesHelper = {
  toISOString: function(d) {
  return d.toISOString().replace(/-/g, "").replace(/:/g, "").replace(/\.\d\d\d/g, "");
  }
};

Diffa.Helpers.CategoriesHelper = {
  extractCategories: function(model, viewCollectionClass) {
    var updateCategories = function() {
      model.rangeCategories.unpack(model.get('categories'));
      model.setCategories.unpack(model.get('categories'));
      model.prefixCategories.unpack(model.get('categories'));
    };

    model.bind('change:categories', updateCategories);

    model.rangeCategories = new Diffa.Collections.CategoryCollection([], {categoryType: 'range'});
    model.setCategories = new Diffa.Collections.CategoryCollection([], {categoryType: 'set'});
    model.prefixCategories = new Diffa.Collections.CategoryCollection([], {categoryType: 'prefix'});

    Diffa.Helpers.DirtyTracker.proxyChange(model, [model.rangeCategories, model.setCategories, model.prefixCategories]);

    updateCategories();
  },
  packCategories: function(model) {
    var categories = {};

    model.rangeCategories.pack(categories);
    model.setCategories.pack(categories);
    model.prefixCategories.pack(categories);

    model.set({categories: categories}, {silent: true});
  }
};

Diffa.Helpers.DirtyTracker = {
  attach: function(model) {
    model.dirty = false;

    model.clearDirty = function() {
      model.dirty = false;
      model.trigger('change:dirty', model, false);
    };

    model.bind('change', function() {
      model.dirty = true;
      model.trigger('change:dirty', model, true);
    })
  },

  proxyChange: function(model, childModels) {
    _.each(childModels, function(childModel) {
      childModel.bind('change', function() { model.trigger('change'); });
    });
  }
};

Diffa.Models.Endpoint = Backbone.Model.extend({
  idAttribute: 'name',
  type: 'endpoint',
  initialize: function() {
    Diffa.Helpers.CategoriesHelper.extractCategories(this);
    Diffa.Helpers.ViewsHelper.extractViews(this, Diffa.Collections.EndpointViews);
    Diffa.Helpers.DirtyTracker.attach(this);
  },
  urlRoot: function() { return "/domains/" + (this.domain || this.collection.domain).id + "/config/endpoints"; },
  prepareForSave: function() {
    Diffa.Helpers.CategoriesHelper.packCategories(this);
    Diffa.Helpers.ViewsHelper.packViews(this);
  },
  validate: function(attrs) {
    console.debug("[Endpoint.validate]")
    var pcm = this.prefixCategories.models
    var rcm = this.rangeCategories.models
    var scm = this.setCategories.models
    var freq = {}
    this.accumulate(freq, pcm);
    this.accumulate(freq, rcm);
    this.accumulate(freq, scm);
    for (var i in freq) {
      if (freq[i] > 1) {
        return "Categories must have unique names: the category name " + i + " was used more than once";
      }
    }
  },
  accumulate: function(freq, catModels) {
    _.each(catModels, function(m) {
      if (freq[m.id]) {
        freq[m.id] += 1;
      } else {
        freq[m.id] = 1;
      }
    });
  },
  uploadInventory: function(f, constraints, opts) {
    $.ajax($.extend({}, {
      url: '/domains/' + this.collection.domain.id + '/inventory/' + this.id + '?' + constraints,
      type: 'POST',
      contentType: 'text/csv',
      data: f,
      processData: false
    }, opts));
  },
  scanningStatus: function() {
    if (this.get('scanUrl')) {
      return "Scanning configured via " + this.get('scanUrl');
    } else {
      return "Scanning not configured";
    }
  },
  inspectionStatus: function() {
    if (this.get('contentRetrievalUrl')) {
      return "Content inspection configured via " + this.get('contentRetrievalUrl');
    } else {
      return "Inspection not configured";
    }
  },
  advancedInterrogationStatus: function() {
    if (this.get('versionGenerationUrl') || this.get('inboundUrl')) {
      return "Advanced interrogation configured"
    } else {
      return "Advanced interrogation not configured"
    }
  },
  categoriesStatus: function() {
    var result = [];
    var applyCategory = function(name, categories) {
      var count = categories.length;
      if (count > 0) {
        result.push(count + " " + name + " categor" + (count > 1 ? "ies" : "y"));
      }
    };

    applyCategory("range", this.rangeCategories);
    applyCategory("set", this.setCategories);
    applyCategory("prefix", this.prefixCategories);

    if (result.length > 0) {
      return result.join(", ");
    } else {
      return "No categories configured";
    }
  },
  viewsStatus: function() {
    if (this.views && this.views.length > 0) {
      var count = this.views.length;

      return count + " view" + (count > 1 ? "s" : "") + " configured";
    } else {
      return "No views configured";
    }
  }
});

Diffa.Models.EndpointView = Backbone.Model.extend({
  idAttribute: 'name',
  initialize: function() {
    Diffa.Helpers.CategoriesHelper.extractCategories(this);
  },
  prepareForSave: function() {
    Diffa.Helpers.CategoriesHelper.packCategories(this);
  }
});

Diffa.Models.Pair = Backbone.Model.extend({
  idAttribute: "key",
  type: 'pair',
  urlRoot: function() { return "/domains/" + this.domain.id + "/config/pairs"; },
  initialize: function() {
    Diffa.Helpers.ViewsHelper.extractViews(this);

    this.actions = new Diffa.Collections.RepairActions(this.get('repairActions'), {pair: this});
    this.escalations = new Diffa.Collections.Escalations(this.get('escalations'), {pair: this});
    this.reports = new Diffa.Collections.Reports(this.get('reports'), {pair: this});

    Diffa.Helpers.DirtyTracker.attach(this);
    Diffa.Helpers.DirtyTracker.proxyChange(this, [this.actions, this.escalations, this.reports]);

    if (this.collection) this.domain = this.collection.domain;
  },
  prepareForSave: function() {
      // Remove properties artifacts from the databinding library
    this.unset('versionPolicyName_text', {silent: true});
    this.unset('upstreamName_text', {silent: true});
    this.unset('downstreamName_text', {silent: true});

    Diffa.Helpers.ViewsHelper.packViews(this);

    this.set({
      repairActions: this.actions.toJSON(),
      escalations: this.escalations.toJSON(),
      reports: this.reports.toJSON()
    });
  },
  fetchExtra: function() {
    this.actions.ensureFetched();
  },
  updateViews: function() {
    this.views.reset(this.get('views'));
  },
  scanningStatus: function() {
    var result = "Manual scanning " + (this.get('allowManualScans') ? "enabled" : "disabled");

    if (this.get('scanCronSpec') && this.get('scanCronEnabled')) {
      result += ". Periodic scanning enabled"
    }

    return result;
  },
  viewsStatus: function() {
    if (this.views && this.views.length > 0) {
      var count = this.views.length;

      return count + " view" + (count > 1 ? "s" : "") + " configured";
    } else {
      return "No views configured";
    }
  },
  repairActionsStatus: function() {
    if (this.actions.length > 0) {
      var count = this.actions.length;

      return count + " repair action" + (count > 1 ? "s" : "") + " configured";
    } else {
      return "No repair actions configured";
    }
  },
  reportsStatus: function() {
    if (this.reports.length > 0) {
      var count = this.reports.length;

      return count + " report" + (count > 1 ? "s" : "") + " configured";
    } else {
      return "No reports configured";
    }
  },
  escalationsStatus: function() {
    if (this.escalations.length > 0) {
      var count = this.escalations.length;

      return count + " escalation" + (count > 1 ? "s" : "") + " configured";
    } else {
      return "No escalations configured";
    }
  }
});

Diffa.Models.PairState = Backbone.Model.extend({
  logPollInterval: 2000,
  initialize: function() {
    var self = this;

    _.bindAll(this, "remove", "syncLog", "startScan", "cancelScan");

    // If we become selected, then we should fetch our actions
    this.bind("change:selected", function(pair) {
      if (pair.get('selected')) {
        self.fetchActions();
        self.fetchReports();
        self.fetchFullDetails();

        self.logPollIntervalId = window.setInterval(self.syncLog, self.logPollInterval);
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
    $.getJSON("/domains/" + this.collection.domain.id + '/actions/' + this.id + '?scope=pair', function(actions) {
      self.set({actions: actions});
    });
  },

  fetchReports: function() {
    var self = this;
    $.getJSON("/domains/" + this.collection.domain.id + '/reports/' + this.id, function(reports) {
      self.set({reports: reports});
    });
  },

  fetchFullDetails: function() {
    var self = this;
    $.getJSON("/domains/" + this.collection.domain.id + '/config/pairs/' + this.id, function(pairInfo) {
      self.set(pairInfo);
      self.set({fullContent: true})
    });
  },

  syncLog: function() {
    var self = this;
    $.getJSON("/domains/" + this.collection.domain.id + "/diagnostics/" + this.id + "/log", function(logEntries) {
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
      url: "/domains/" + this.collection.domain.id + "/scanning/pairs/" + this.id + "/scan",
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
      url: "/domains/" + this.collection.domain.id + "/scanning/pairs/" + this.id + "/scan",
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
      url: "/domains/" + this.collection.domain.id + "/reports/" + this.id + "/" + name,
      type: "POST",
      error: function(jqXHR, textStatus, errorThrown) {
        alert("Error in report request: " + errorThrown);
      }
    });
  }
});
Diffa.Models.Category = Backbone.Model.extend({
  idAttribute: "name"
});
Diffa.Models.RepairAction = Backbone.Model.extend({
  idAttribute: "name"
});
Diffa.Models.Escalation = Backbone.Model.extend({
  idAttribute: "name"
});
Diffa.Models.Report = Backbone.Model.extend({
  idAttribute: "name"
});

Diffa.Collections.Watchable = {
  // Indicates that the given element is watching this collection, and it should periodically update itself.
  watch: function(listenerEl) {
    if (!this.watched) {
      var self = this;

      this.watched = true;
      this.sync();

      this.registeredWatchIntervalId = setInterval(function() { self.sync(); }, this.watchInterval);

      // TODO: If we want to support listening elements coming and going, we should keep track of the listener element
      //       and only keep polling if we have at least one that is still in the DOM.
    }
  }
};

Diffa.Collections.CollectionBase = Backbone.Collection.extend(Diffa.Collections.Watchable).extend({
  initialize: function(models, opts) {
    var self = this;

    this.domain = opts.domain;

    // Track whether an initial load has been done. This allows a UI to distinguish
    // between "still loading" and "empty".
    this.initialLoadComplete = false;
    this.bind("reset", function() {
      self.initialLoadComplete = true;
    });
  },

  ensureFetched: function(cb) {
    var self = this;

    var complete = function() { if (cb) cb(); };
    if (this.initialLoadComplete) {
      complete();
      return;
    }

    if (!this.initialFetchFuture) {
      self.initialFetchFuture = new $.Deferred();
      self.initialFetchFuture.done(complete);

      this.fetch({
        success: function() {
          self.initialFetchFuture.resolve();
        }
      });
    } else {
      self.initialFetchFuture.done(complete);
    }
  }
});

Diffa.Collections.Endpoints = Diffa.Collections.CollectionBase.extend({
  model: Diffa.Models.Endpoint,
  url: function() { return "/domains/" + this.domain.id + "/config/endpoints"; },
  comparator: function(endpoint) { return endpoint.get('name'); }
});

Diffa.Collections.EndpointViews = Backbone.Collection.extend({
  model: Diffa.Models.EndpointView
});

Diffa.Collections.Pairs = Diffa.Collections.CollectionBase.extend({
  model: Diffa.Models.Pair,
  url: function() { return "/domains/" + this.domain.id + "/config/pairs"; },
  comparator: function(pair) { return pair.id; }
});

Diffa.Models.HiddenPair = Backbone.Model.extend({
  initialize: function(model, opts) {
    this.id = model.id;
    this.user = opts.collection.user;
    this.domain = opts.collection.domain.id;
  },
  url: function() {
    return "/users/" + this.user + "/" + this.domain + "/" + this.id + "/filter/SWIM_LANE";
  }
});

Diffa.Collections.HiddenPairs = Diffa.Collections.CollectionBase.extend({
  model: Diffa.Models.HiddenPair,
  initialize: function(models, opts) {
    this.user = opts.user;
    this.domain = opts.domain;
    this.fetch({
      success: function() {
        this.sync();
      },
      error: function() {}
    });
  },
  url: function() {
    return "/users/" + this.user + "/" + this.domain.id + "/filter/SWIM_LANE";
  },
  parse: function(response) {
    return response.map(this.identify);
  },
  identify: function(ident) {
    return {id: ident};
  },
  comparator: function(pair) {
    return pair.get("id");
  },
  remove: function(models, options) {
    var self = this;
    _.each(models, function(model) {
      if (model && model.destroy) {
        model.destroy();
      }
    });
    self.fetch();
  },
  hidePair: function(pairKey) {
    var model = new Diffa.Models.HiddenPair({id: pairKey}, {collection: this});
    this.create(model);
  },
  revealPair: function(pairKey) {
    var model = this.get({id: pairKey});
    if (model) {
      this.remove([model]);
    }
  }
});

Diffa.Collections.CategoryCollection = Backbone.Collection.extend({
  model: Diffa.Models.Category,
  initialize: function(models, options) {
    Diffa.Collections.CategoryCollection.__super__.initialize.call(this, models, options);
    this.categoryType = options.categoryType;
  },
  pack: function(target) {
    var self = this;

    this.each(function(cat) {
      target[cat.get('name')] = $.extend({}, cat.attributes, {'@type': self.categoryType});
      delete target[cat.get('name')].name;
      delete target[cat.get('name')].dataType_text;
    });
  },
  unpack: function(source) {
    var self = this;

    this.reset([]);
    _.each(source, function(value, name) {
      if (value['@type'] == self.categoryType) {
        var attrs = $.extend({}, value, {name: name});
        delete attrs['@type']

        self.add(new self.model(attrs));
      }
    });
  }
});
Diffa.Collections.RepairActions = Diffa.Collections.CollectionBase.extend({
  model: Diffa.Models.RepairAction
});
Diffa.Collections.Escalations = Diffa.Collections.CollectionBase.extend({
  model: Diffa.Models.Escalation
});
Diffa.Collections.Reports = Diffa.Collections.CollectionBase.extend({
  model: Diffa.Models.Report
});

Diffa.Collections.PairStates = Diffa.Collections.CollectionBase.extend({
  watchInterval: 5000,        // We poll for pair status updates every 5s
  model: Diffa.Models.PairState,
  url: function() { return "/domains/" + this.domain.id + "/scanning/states"; },

  initialize: function(models, options) {
    Diffa.Collections.PairStates.__super__.initialize.call(this, models, options);

    _.bindAll(this, "sync", "select");

    this.bind("add", function(pair) {
      pair.set({selected: pair.id == this.selectedPair});
    });
  },

  comparator: function(state) { return state.id; },

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

  select: function(pairKey) {
    this.selectedPair = pairKey;
    this.each(function(pair) {
      pair.set({selected: pair.id == pairKey});
    });
  }
});

/**
 * Mixin providing the ability for a model to retrieve aggregates. Note the following requirements:
 *  - initialize should be called within the scope of the mixee to init internal variables;
 *  - this.domain (and optionally this.pair) should be set to configure the domain/pair to poll for aggregates;
 */
Diffa.Models.Aggregator = {
  initialize: function() {
    // Start with an empty list of aggregate requests
    this.aggregateRequests = {};
  },

  retrieveAggregates: function(callback) {
    var self = this;

    var lastRequestDetails = {};
    var params = $.map(this.aggregateRequests, function(request, name) {
      // Allow requests to be functions
      var requestDetails = request;
      if (_.isFunction(request)) {
        requestDetails = request();
      }
      lastRequestDetails[name] = requestDetails;

      var aggString = "";
      if (requestDetails.startTime) aggString += Diffa.Helpers.DatesHelper.toISOString(requestDetails.startTime);
      aggString += "-";
      if (requestDetails.endTime) aggString += Diffa.Helpers.DatesHelper.toISOString(requestDetails.endTime);
      if (requestDetails.bucketing) aggString += "@" + requestDetails.bucketing;

      return "agg-" + name + "=" + aggString;
    }).join("&");

    var path = "/domains/" + self.domain.id + "/diffs/aggregates";
    if (self.pair) path += "/" + self.pair;

    $.getJSON(path + "?" + params, function(data) {
      callback(lastRequestDetails, data);
    });
  },

  subscribeAggregate: function(name, request) {
    this.aggregateRequests[name] = request;
  },
  unsubscribeAggregrate: function(name) {
    delete this.aggregateRequests[name];
  }
};

Diffa.Models.PairAggregates = Backbone.Model.extend(Diffa.Collections.Watchable).extend(Diffa.Models.Aggregator).extend({
  idAttribute: 'pair',
  watchInterval: 5000,

  initialize: function() {
    Diffa.Models.Aggregator.initialize.call(this);

    _.bindAll(this, 'sync', 'applyAggregateResult', 'retrieveAggregates');

    this.domain = this.get('domain');
    this.pair = this.get('pair');
  },

  sync: function(callback, opts) {
    var self = this;

    this.retrieveAggregates(function(requestDetails, result) {
      self.applyAggregateResult(requestDetails, result, opts);

      if (callback) callback();
    });
  },

  applyAggregateResult: function(requestDetails, result, opts) {
    this.set(result, opts);
    
    this.lastRequests = requestDetails;
  },

  getMap: function() {
    return this.get('map');
  },

  containsMultiplePairs: false
});

Diffa.Collections.DomainAggregates = Backbone.Collection.extend(Diffa.Models.Aggregator).extend({
  model: Diffa.Models.PairAggregates,

  initialize: function(models, opts) {
    Diffa.Models.Aggregator.initialize.call(this);

    _.bindAll(this, 'sync', 'retrieveAggregates');

    this.domain = opts.domain;
  },

  /** Sort contained pairs by id to give stable iteration */
  comparator: function(pair) { return pair.id; },

  sync: function(callback, opts) {
    var self = this;

    this.retrieveAggregates(function(requestDetails, data) {
      for(var pair in data) {
        self.forPair(pair).applyAggregateResult(requestDetails, data[pair], opts);
      }

      if (callback) callback();
    });
  },

  forPair: function(pair, opts) {
    var pairAggregates = this.get(pair);
    if (!pairAggregates) {
      pairAggregates = new Diffa.Models.PairAggregates({domain: this.domain, pair: pair});
      this.add(pairAggregates, opts);
    }

    return pairAggregates;
  },

  change: function() {
    this.forEach(function(pair) { pair.change(); });
  },

  getMap: function(pairKey) {
    var pairAggregate = this.get(pairKey);
    if (pairAggregate) {
      return pairAggregate.getMap();
    } else {
      return [];
    }
  },

  containsMultiplePairs: true
});

/**
 * Root object constructed to create a Diffa domain, and all constituent collections.
 */
Diffa.Models.Domain = Backbone.Model.extend({
  idAttribute: 'name',
  initialize: function() {
    var self = this;
    var user = this.get('user');
    this.endpoints = new Diffa.Collections.Endpoints([], {domain: this});
    this.pairs = new Diffa.Collections.Pairs([], {domain: this});
    this.pairStates = new Diffa.Collections.PairStates([], {domain: this});
    this.diffs = new Diffa.Collections.Diffs([], {domain: this});
    this.aggregates = new Diffa.Collections.DomainAggregates([], {domain: this});
    this.hiddenPairs = new Diffa.Collections.HiddenPairs([], {domain: this, user: user});
  },

  loadAll: function(colls, callback) {
    var self = this;
    var remaining = colls.length;
    _.each(colls, function(preload) {
      self[preload].ensureFetched(function() {
        remaining -= 1;

        if (remaining == 0) {
          if (callback) { callback(); }
        }
      });
    });
  },

  createAggregator: function(pair) {
    return new Diffa.Models.Aggregator({domain: this, pair: pair});
  }
});

/**
 * Cache of domains that are already in use on the page. This lets us share domains between multiple views.
 */
Diffa.DomainManager = _.extend({}, Backbone.Events, {
  domains: {},
  get: function(name, user) {
    var domain = this.domains[name];

    if (!domain) {
      domain = new Diffa.Models.Domain({name: name, user: user});
      this.domains[name] = domain;
    }

    return domain;
  }
});
_.bindAll(Diffa.DomainManager, 'get');
