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

var Diffa = {
  Routers: {},
  Views: {},
  Collections: {},
  Models: {},
  Config: {},
  Binders: {},
  Helpers: {}
};

$(function() {

Diffa.Routers.Config = Backbone.Router.extend({
  routes: {
    "":                   "index",          // #
    "endpoint":           "createEndpoint", // #endpoint
    "endpoint/:endpoint": "manageEndpoint", // #endpoint/ep1
    "pair":               "createPair",     // #pair
    "pair/:pair":         "managePair"      // #pair/p1
  },

  index: function() {
    this.updateEditor(null);
  },

  createEndpoint: function() {
    var newEndpoint = new Diffa.Models.Endpoint();
    this.updateEditor(function() { return new Diffa.Views.EndpointEditor({model: newEndpoint, collection: Diffa.EndpointsCollection}) });
  },
  createPair: function() {
    var newPair = new Diffa.Models.Pair();
    this.updateEditor(function() { return new Diffa.Views.PairEditor({model: newPair, collection: Diffa.PairsCollection}) });
  },

  manageEndpoint: function(endpointName) {
    var endpoint = Diffa.EndpointsCollection.get(endpointName);
    this.updateEditor(function() { return new Diffa.Views.EndpointEditor({model: endpoint, collection: Diffa.EndpointsCollection}) });
  },

  managePair: function(pairName) {
    var pair = Diffa.PairsCollection.get(pairName);
    this.updateEditor(function() { return new Diffa.Views.PairEditor({model: pair, collection: Diffa.PairsCollection}) });
  },

  updateEditor: function(newEditorBuilder) {
    if (this.currentEditor) {
      this.currentEditor.close();
    }
    if (newEditorBuilder) {
      this.currentEditor = newEditorBuilder();
    } else {
      this.currentEditor = null;
    }
  }
});

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
})
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
      var name = cat.get('name') || "";

      target[name] = $.extend({}, cat.attributes, {'@type': self.categoryType});
      delete target[name].name;
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

Diffa.Views.ElementList = Backbone.View.extend({
  initialize: function() {
    var self = this;

    _.bindAll(this, "render", "addOne", "updateNoneMessage");

    this.collection.bind('reset', this.render);
    this.collection.bind('add', function(m) { self.addOne(m, false); });
    this.collection.bind('remove', this.updateNoneMessage);

    this.render();
  },

  render: function() {
    var self = this;

    this.collection.each(function(m) {
      self.addOne(m, true);
    });

    this.updateNoneMessage();

    return this;
  },

  addOne: function(m, initialRender) {
    var self = this;
    var newView = new Diffa.Views.ElementListItem({model: m, elementType: self.options.elementType});

    this.$('.element-list').append(newView.render());
    this.updateNoneMessage();

    if (!initialRender) newView.flash();
  },

  updateNoneMessage: function() {
    this.$('.none-message').toggle(this.collection.length == 0 && !!this.collection.initialLoadComplete);
    this.$('.loading').toggle(this.collection.length == 0 && !this.collection.initialLoadComplete);
  }
});
Diffa.Views.ElementListItem = Backbone.View.extend({
  tagName: 'li',

  initialize: function() {
    _.bindAll(this, 'render', 'flash', 'remove');

    this.model.bind("change:" + this.model.idField, this.render);
    this.model.bind("destroy", this.remove);
  },

  render: function() {
    $(this.el).html(
      '<a href="#' + this.options.elementType + '/' + this.model.id + '">' +
        this.model.id +
      '</a>');

    return this.el;
  },

  flash: function() {
    $('a', this.el).css('background-color', '#ffff99').animate({'background-color': '#FFFFFF'});
  }
});
Diffa.Views.FormEditor = Backbone.View.extend({
  events: {
    "click .save": 'saveChanges',
    "click .delete": 'deleteObject'
  },

  initialize: function() {
    _.bindAll(this, 'render', 'close');

    this.model.bind('fetch', this.render);

    this.render();
  },

  render: function() {
    this.hideErrors();
    this.preBind();

    $('input[data-key]', this.el).val('');    // Clear the contents of all bound fields
    Backbone.ModelBinding.bind(this, {all: "data-key"});

    var nameContainer = $('.name-container', this.el);

    $('input', nameContainer).toggle(this.model.isNew());
    $('span', nameContainer).toggle(!this.model.isNew());

    $(this.el).show();
  },

  // Callback function to be implemented by subclasses that need to add field values before binding.
  preBind: function() {},
  postClose: function() {},

  close: function() {
    $(this.el).hide();

    this.undelegateEvents();
    Backbone.ModelBinding.unbind(this);

    this.postClose();
  },

  saveChanges: function() {
    var self = this;
    var saveButton = $('.save');

    saveButton.attr('disabled', 'disabled');

    this.hideErrors();
    this.model.prepareForSave();
    this.model.save({}, {
      global: false,        // Don't invoke global event handlers - we'll deal with errors here locally
      success: function() {
        saveButton.removeAttr('disabled');

        if (!self.collection.get(self.model.id)) {
          self.collection.add(self.model);
          Diffa.SettingsApp.navigate(self.elementType + "/" + self.model.id, {replace: true, trigger: true});
        }
      },
      error: function(model, response) {
        saveButton.removeAttr('disabled');

        self.showError(response.responseText);
      }
    });
  },

  deleteObject: function() {
    this.model.destroy({
      success: function() {
        Diffa.SettingsApp.navigate("", {trigger: true});
      }
    });
  },

  showError: function(errorHtml) {
    $.scrollTo(this.$('.error').html(errorHtml).show(), 1000);
  },
  hideErrors: function() {
    this.$('.error').hide();
  }
});
Diffa.Views.EndpointEditor = Diffa.Views.FormEditor.extend({
  el: $('#endpoint-editor'),
  elementType: "endpoint",

  preBind: function() {
    // Attach categories
    this.categoryEditors = [
      new Diffa.Views.CategoriesEditor({collection: this.model.rangeCategories, el: this.$('.range-categories')}),
      new Diffa.Views.CategoriesEditor({collection: this.model.setCategories, el: this.$('.set-categories')}),
      new Diffa.Views.CategoriesEditor({collection: this.model.prefixCategories, el: this.$('.prefix-categories')})
    ];

    this.viewsEditor = new Diffa.Views.EndpointViewsEditor({collection: this.model.views, el: this.$('.views')});
  },
  postClose: function() {
    if (this.categoryEditors) _.each(this.categoryEditors, function(editor) { editor.close(); });
    if (this.viewsEditor) this.viewsEditor.close();
  }
});
Diffa.Views.PairEditor = Diffa.Views.FormEditor.extend({
  el: $('#pair-editor'),
  elementType: "pair",

  preBind: function() {
    var selections = this.$('select.endpoint-selection');

    selections.empty();
    Diffa.EndpointsCollection.each(function(ep) {
      selections.append('<option value="' + ep.get('name') + '">' + ep.get('name') + '</option>');
    });

    this.viewsEditor = new Diffa.Views.PairViewsEditor({collection: this.model.views, el: this.$('.views')});
  },
  postClose: function() {
    if (this.viewsEditor) this.viewsEditor.close();
  }
});

Diffa.Views.TableEditor = Backbone.View.extend({
  rowEditor: undefined,   /* Must be overriden by subclasses */

  initialize: function() {
    _.bindAll(this, "addOne", "render", "createRow");
    this.collection.bind("add", this.addOne);
    this.collection.bind("reset", this.render);

    this.$('>.add-link').click(this.createRow);

    this.templateName = this.$('table').data('template');
    if (!this.templateName) console.error("Missing template name", this);
    this.template = _.template($('#' + this.templateName).html());

    this.table = this.$('>table');

    this.render();
  },

  render: function() {
    // Remove all category rows
    $('tr.editable-row', this.table).remove();

    this.collection.each(this.addOne);
  },

  addOne: function(added) {
    // Create a row with the table template
    var row = $(this.template()).addClass('editable-row').appendTo(this.table);

    // Bind the model to the row
    var rowView = new this.rowEditor({el: row, model: added});
  },

  createRow: function(e) {
    e.preventDefault();

    this.collection.add(new this.collection.model({}));
  },

  close: function() {
    this.undelegateEvents();

    this.collection.unbind("add", this.addOne);
    this.collection.unbind("reset", this.render);
  }
});
Diffa.Views.CategoryEditor = Backbone.View.extend({
  initialize: function() {
    Backbone.ModelBinding.bind(this, {all: "data-el-key"});

    new Diffa.Binders.ListBinder(this, "data-el-list-key");
  }
});
Diffa.Views.CategoriesEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.CategoryEditor
});
Diffa.Views.PairViewEditor = Backbone.View.extend({
  initialize: function() {
    Backbone.ModelBinding.bind(this, {all: "data-el-key"});
  }
});
Diffa.Views.PairViewsEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.PairViewEditor
});
Diffa.Views.EndpointViewEditor = Backbone.View.extend({
  initialize: function() {
    this.categoryEditors = [
      new Diffa.Views.CategoriesEditor({collection: this.model.rangeCategories, el: this.$('.range-categories')}),
      new Diffa.Views.CategoriesEditor({collection: this.model.setCategories, el: this.$('.set-categories')}),
      new Diffa.Views.CategoriesEditor({collection: this.model.prefixCategories, el: this.$('.prefix-categories')})
    ];
    Backbone.ModelBinding.bind(this, {all: "data-el-key"});
  }
});
Diffa.Views.EndpointViewsEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.EndpointViewEditor
});


Diffa.Binder = function(options) {
  this.initialize.apply(this, arguments);
};
_.extend(Diffa.Binder.prototype, {
  initialize: function() {}
});
Diffa.Binder.extend = Backbone.Model.extend;    // Copy the extend method definition from a backbone class
Diffa.Binders.ListBinder = Diffa.Binder.extend({
  initialize: function(view, key) {
    var self = this;

    _.bindAll(this, 'renderEl');
    this.view = view;

    view.$('[' + key + ']').each(function(idx, el) {
      var attrName = $(el).attr(key);

      self.renderEl(attrName, el);
      view.model.bind('change:' + attrName, function() { self.renderEl(attrName, el); });
    });
  },

  renderEl: function(attrName, el) {
    var self = this;
    var values = self.view.model.get(attrName);

    $(el).empty();
    _.each(values, function(val, idx) {
      $('<input type="text" value="' + _.escape(val) + '">').
        appendTo(el).
        change(function() {
          values[idx] = $(this).val();
        });
    });

    $('<button>+</button>').
      appendTo(el).
      click(function() {
        self.view.model.set(attrName, (self.view.model.get(attrName) || []).concat(""))
      });
  }
});

Diffa.currentDomain = currentDiffaDomain;
Diffa.SettingsApp = new Diffa.Routers.Config();
Diffa.EndpointsCollection = new Diffa.Collections.Endpoints();
Diffa.PairsCollection = new Diffa.Collections.Pairs();
Diffa.EndpointElementListView = new Diffa.Views.ElementList({
  el: $('#endpoints-list'),
  collection: Diffa.EndpointsCollection,
  elementType: 'endpoint'
});
Diffa.EndpointElementListView = new Diffa.Views.ElementList({
  el: $('#pairs-list'),
  collection: Diffa.PairsCollection,
  elementType: 'pair'
});

var preloadCollections = function(colls, callback) {
  var remaining = colls.length;
  _.each(colls, function(preload) {
    preload.fetch({
      success: function() {
        remaining -= 1;

        if (remaining == 0) {
          callback();
        }
      }
    })
  });
};

// Preload useful collections, and then start processing history
preloadCollections([Diffa.EndpointsCollection, Diffa.PairsCollection], function() {
  Backbone.history.start();
});
});