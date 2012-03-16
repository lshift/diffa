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
  urlRoot: function() { return "/domains/" + Diffa.currentDomain + "/config/endpoints"; },
  prepareForSave: function() {
    Diffa.Helpers.CategoriesHelper.packCategories(this);
    Diffa.Helpers.ViewsHelper.packViews(this);
  },
  uploadInventory: function(f, constraints, opts) {
    $.ajax($.extend({}, {
      url: '/domains/' + Diffa.currentDomain + '/inventory/' + this.id + '?' + constraints,
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
  urlRoot: function() { return "/domains/" + Diffa.currentDomain + "/config/pairs"; },
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

Diffa.Collections.CollectionBase = Backbone.Collection.extend({
  initialize: function() {
    var self = this;

    // Track whether an initial load has been done. This allows a UI to distinguish
    // between "still loading" and "empty".
    this.initialLoadComplete = false;
    this.bind("reset", function() {
      self.initialLoadComplete = true;
    });
  }
});
Diffa.Collections.Endpoints = Diffa.Collections.CollectionBase.extend({
  model: Diffa.Models.Endpoint,
  url: function() { return "/domains/" + Diffa.currentDomain + "/config/endpoints"; },
  comparator: function(endpoint) { return endpoint.get('name'); }
});
Diffa.Collections.EndpointViews = Backbone.Collection.extend({
  model: Diffa.Models.EndpointView
});
Diffa.Collections.Pairs = Diffa.Collections.CollectionBase.extend({
  model: Diffa.Models.Pair,
  url: function() { return "/domains/" + Diffa.currentDomain + "/config/pairs"; },
  comparator: function(endpoint) { return endpoint.get('name'); }
});
Diffa.Collections.CategoryCollection = Backbone.Collection.extend({
  model: Backbone.Model,
  initialize: function(models, options) {
    Diffa.Collections.CategoryCollection.__super__.initialize.call(this, arguments);
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

/**
 * Root object constructed to create a Diffa domain, and all constituent collections.
 */
Diffa.Models.Domain = Backbone.Model.extend({
  initialize: function() {
    this.endpoints = new Diffa.Collections.Endpoints();
    this.pairs = new Diffa.Collections.Pairs();
  }
});