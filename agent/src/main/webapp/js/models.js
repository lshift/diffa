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
    updateViews();
  },
  packViews: function(model) {
    model.views.each(function(m) { if (m.prepareForSave) m.prepareForSave(); });
    model.set({views: model.views.toJSON()}, {silent: true});
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
Diffa.Models.Endpoint = Backbone.Model.extend({
  idAttribute: 'name',
  initialize: function() {
    Diffa.Helpers.CategoriesHelper.extractCategories(this);
    Diffa.Helpers.ViewsHelper.extractViews(this, Diffa.Collections.EndpointViews);
  },
  urlRoot: function() { return "/domains/" + (this.domain || this.collection.domain).id + "/config/endpoints"; },
  prepareForSave: function() {
    Diffa.Helpers.CategoriesHelper.packCategories(this);
    Diffa.Helpers.ViewsHelper.packViews(this);
  },
  uploadInventory: function(f, constraints, opts) {
    $.ajax($.extend({}, {
      url: '/domains/' + this.collection.domain.id + '/inventory/' + this.id + '?' + constraints,
      type: 'POST',
      contentType: 'text/csv',
      data: f,
      processData: false
    }, opts));
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
  urlRoot: function() { return "/domains/" + (this.domain || this.collection.domain).id + "/config/pairs"; },
  initialize: function() {
    Diffa.Helpers.ViewsHelper.extractViews(this);
  },
  prepareForSave: function() {
      // Remove properties artifacts from the databinding library
    this.unset('versionPolicyName_text', {silent: true});
    this.unset('upstreamName_text', {silent: true});
    this.unset('downstreamName_text', {silent: true});

    Diffa.Helpers.ViewsHelper.packViews(this);
  },
  updateViews: function() {
    this.views.reset(this.get('views'));
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

Diffa.Collections.Watchable = {
  // Indicates that the given element is watching this collection, and it should periodically update itself.
  watch: function(listenerEl) {
    if (!this.watched) {
      this.watched = true;
      this.sync();

      this.registeredWatchIntervalId = setInterval(this.sync, this.watchInterval);

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
    var complete = function() { if (cb) cb(); };
    if (this.initialLoadComplete) {
      complete();
      return;
    }

    if (!this.startedInitialFetch) {
      this.startedInitialFetch = true;
      this.fetch({
        success: function() {
          complete();
        }
      });
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
  comparator: function(endpoint) { return endpoint.get('name'); }
});
Diffa.Collections.CategoryCollection = Backbone.Collection.extend({
  model: Backbone.Model,
  initialize: function(models, options) {
    Diffa.Collections.CategoryCollection.__super__.initialize.call(this, models, options);
    this.categoryType = options.categoryType;
  },
  pack: function(target) {
    var self = this;

    this.each(function(cat) {
      target[cat.get('name')] = $.extend({}, cat.attributes, {'@type': self.categoryType});
      delete target[cat.get('name')].name;
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
Diffa.Collections.PairStates = Diffa.Collections.CollectionBase.extend({
  watchInterval: 5000,        // We poll for pair status updates every 5s
  model: Diffa.Models.PairState,
  url: function() { return "/domains/" + this.domain.id + "/scanning/states"; },

  initialize: function(models, options) {
    Diffa.Collections.PairStates.__super__.initialize.call(this, models, options);

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
      url: "/domains/" + this.domain.id + "/scanning/scan_all",
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

/**
 * Root object constructed to create a Diffa domain, and all constituent collections.
 */
Diffa.Models.Domain = Backbone.Model.extend({
  idAttribute: 'name',
  initialize: function() {
    this.endpoints = new Diffa.Collections.Endpoints([], {domain: this});
    this.pairs = new Diffa.Collections.Pairs([], {domain: this});
    this.pairStates = new Diffa.Collections.PairStates([], {domain: this});
    this.blobs = new Diffa.Models.Blobs({domain: this});
    this.diffs = new Diffa.Collections.Diffs([], {domain: this});
  },

  loadAll: function(colls, callback) {
    var self = this;
    var remaining = colls.length;
    _.each(colls, function(preload) {
      self[preload].ensureFetched(function() {
        remaining -= 1;

        if (remaining == 0) {
          callback();
        }
      });
    });
  }
});

/**
 * Cache of domains that are already in use on the page. This lets us share domains between multiple views.
 */
Diffa.DomainManager = _.extend({}, Backbone.Events, {
  domains: {},
  get: function(name) {
    if (!name) {
      name = "diffa";     // Default the domain name when not specified
    }

    var domain = this.domains[name];

    if (!domain) {
      domain = new Diffa.Models.Domain({name: name});
      this.domains[name] = domain;
    }

    return domain;
  }
});
_.bindAll(Diffa.DomainManager, 'get');