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

Diffa.Routers.Config = Backbone.Router.extend({
  routes: {
    "":                         "index",          // #
    "endpoint":                 "createEndpoint", // #endpoint
    "endpoint/:endpoint":       "manageEndpoint", // #endpoint/ep1
    "endpoint/:endpoint/:pane": "manageEndpoint", // #endpoint/ep1/scanning
    "pair":                     "createPair",     // #pair
    "pair/:pair":               "managePair",     // #pair/p1
    "pair/:pair/:pane":         "managePair"      // #pair/p1/views
  },

  initialize: function(opts) {
    var self = this;
    this.domain = opts.domain;
    this.el = opts.el;

    this.selectorEl = $('.diffa-element-selector', this.el);
    this.endpointEditorEl = $('.diffa-endpoint-editor', this.el);
    this.pairEditorEl = $('.diffa-pair-editor', this.el);
    this.breadcrumbsEl = $('.diffa-settings-crumbs', this.el);

    $(this.el).on('pair:saved', function(event, pairId) {
      self.navigate("pair/" + pairId, {replace: true, trigger: true});
    });
    $(this.el).on('endpoint:saved', function(event, endpointId) {
      self.navigate("endpoint/" + endpointId, {replace: true, trigger: true});
    });
    $(this.el).on('endpoint:deleted', function() {
      self.navigate("", {trigger: true});
    });
    $(this.el).on('pair:deleted', function() {
      self.navigate("", {trigger: true});
    });
  },

  index: function() {
    this.update({selector: true});
  },

  createEndpoint: function() {
    this.update({endpoint: ''});
  },
  createPair: function() {
    this.update({pair: ''});
  },

  manageEndpoint: function(endpointName, pane) {
    this.update({endpoint: endpointName, endpointPane: pane});
  },

  managePair: function(pairName, pane) {
    this.update({pair: pairName, pairPane: pane});
  },

  update: function(opts) {
    if (!opts.pair) this.pairEditorEl.removeData('pair').removeData('pane').trigger('changed:pair');
    if (!opts.endpoint) this.endpointEditorEl.removeData('endpoint').removeData('pane').trigger('changed:endpoint');
    if (!opts.selector) this.selectorEl.hide();

    this.breadcrumbsEl.removeData('pair').removeData('endpoint').removeData('pane');


    if (opts.pair != undefined) {
      this.pairEditorEl.data('pair', opts.pair).data('pane', opts.pairPane || 'root').trigger('changed:pair');
      this.breadcrumbsEl.data('pair', opts.pair);
    }
    if (opts.endpoint != undefined) {
      this.endpointEditorEl.data('endpoint', opts.endpoint).data('pane', opts.endpointPane || 'root').trigger('changed:endpoint');
      this.breadcrumbsEl.data('endpoint', opts.endpoint);
    }
    if (opts.selector)
      this.selectorEl.show();

    if (opts.pairPane) this.breadcrumbsEl.data('pane', opts.pairPane);
    if (opts.endpointPane) this.breadcrumbsEl.data('pane', opts.endpointPane);

    this.breadcrumbsEl.trigger('changed');
  }
});

Diffa.Views.ElementSelector = Backbone.View.extend({
  template: window.JST['settings/elementselector'],

  initialize: function(opts) {
    this.domain = opts.domain;

    this.render();
  },
  render: function() {
    $(this.el).html(this.template({domain: this.domain.id}));
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

    var index = this.collection.indexOf(m);
    if (index == 0)
      this.$('.element-list').prepend(newView.render());
    else
      this.$('.element-list').children().eq(index - 1).after(newView.render());

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
Diffa.Views.SettingsBreadcrumb = Backbone.View.extend({
  events: {
    'changed': 'render'
  },

  initialize: function() {
    this.render();
  },

  render: function() {
    var endpoint = $(this.el).data('endpoint');
    var pair = $(this.el).data('pair');
    var pane = $(this.el).data('pane');

    $(this.el).html('<a href="#">Settings</a>');

    if (pair) {
      $(this.el).append(' > <a href="#pair/' + pair + '">Pair: ' + pair + '</a>');

      if (pane) {
        $(this.el).append(' > <a href="#pair/' + pair + '/' + pane + '">' + pane + '</a>');
      }
    } else if (endpoint) {
      $(this.el).append(' > <a href="#endpoint/' + endpoint+ '">endpoint: ' + endpoint + '</a>');

      if (pane) {
        $(this.el).append(' > <a href="#endpoint/' + endpoint + '/' + pane + '">' + pane + '</a>');
      }
    }
  }
});
Diffa.Views.FormEditor = Backbone.View.extend({
  events: {
    "click .save": 'saveChanges',
    "click .delete": 'deleteObject'
  },

  initialize: function() {
    _.bindAll(this, 'render', 'close');

    this.pane = 'root';     // Set our default pane

    // If we have a template, then instantiate it and re-bind events (since the default backbone event binding will have
    // failed).
    if (this.template) {
      $(this.el).html(this.template());
      this.delegateEvents(this.events);
    }

    this.render();
    Diffa.Helpers.ChangeMonitor.monitorInputsForChanges(this.el);

    // :visible because a form may have its first input as hidden
    this.$("input:visible").first().focus();
  },

  render: function() {
    this.hideErrors();
    this.preBind();

    // Clear the contents of all bound fields, except for radio buttons
    $('input[data-key]', this.el).not('input:radio').val(''); 
    var binding = {};
    binding[this.model.type] = this.model;
    this.viewBinding = rivets.bind(this.el, binding);

    this.$('.feature-table').toggle(!!this.model.isSaved);
    this.$('.features-not-available').toggle(!this.model.isSaved);

    this.postBind();

    var nameContainer = $('.name-container', this.el);

    $('input', nameContainer).toggle(this.model.isNew());
    $('span', nameContainer).toggle(!this.model.isNew());

    this.showHidePanes();
    this.linkPanes();

    $(this.el).show();
  },

  setPane: function(pane) {
    this.pane = pane;
    this.showHidePanes();
  },

  showHidePanes: function() {
    this.$('.pane').hide();
    this.$('.pane[data-pane=' + this.pane + ']').show();

    this.$('.root-controls').toggle(this.pane == 'root');
    this.$('.pane-controls').toggle(this.pane != 'root');
  },

  linkPanes: function() {
    var self = this;

    this.$('tr[data-target-pane] .key').each(function() {
      var keyField = $(this);
      var targetPane = keyField.closest('[data-target-pane]').data('target-pane');
      var linkTarget = '#' + self.model.type + '/' + self.model.id + '/' + targetPane;

      var currentLink = keyField.find('a');
      if (currentLink.length > 0) {
        currentLink.attr('href', linkTarget);
      } else {
        keyField.wrapInner('<a href="' + linkTarget + '"/>');
      }
    });

    this.$('a.return').attr('href', '#' + self.model.type + '/' + self.model.id);
  },

  // Callback function to be implemented by subclasses that need to add field values before binding.
  preBind: function() {},
  postBind: function() {},
  postClose: function() {},

  close: function() {
    $(this.el).hide();

    this.undelegateEvents();
    this.viewBinding.unbind();

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
        self.model.clearDirty();

        if (!self.collection.get(self.model.id)) {
          self.collection.add(self.model);
          $(self.el).
            data(self.elementType, self.model.id).
            trigger(self.elementType + ':saved', [self.model.id]);
        }
      },
      error: function(model, response) {
        saveButton.removeAttr('disabled');

        self.showError(response.responseText || response);
      }
    });
  },

  deleteObject: function() {
    var self = this;

    this.model.destroy({
      success: function() {
        $(self.el).
          removeData(self.elementType).
          trigger(self.elementType + ':deleted', [self.model.id]);
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
  elementType: "endpoint",
  template: window.JST['settings/endpointeditor'],

  postBind: function() {
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
  elementType: "pair",
  template: window.JST['settings/paireditor'],

  preBind: function() {
    var selections = this.$('select.endpoint-selection');
    var domain = this.collection.domain;

    selections.empty();
    domain.endpoints.each(function(ep) {
      selections.append('<option value="' + ep.get('name') + '">' + ep.get('name') + '</option>');
    });
  },
  postBind: function() {
    this.viewsEditor = new Diffa.Views.PairViewsEditor({collection: this.model.views, el: this.$('.views')});
    this.actionsEditor = new Diffa.Views.RepairActionsEditor({collection: this.model.actions, el: this.$('.repair-actions')});
    this.reportsEditor = new Diffa.Views.ReportsEditor({collection: this.model.reports, el: this.$('.reports')});
    this.escalationsEditor = new Diffa.Views.EscalationsEditor({collection: this.model.escalations, el: this.$('.escalations')});
  },
  postClose: function() {
    if (this.viewsEditor) this.viewsEditor.close();
  }
});

Diffa.Views.TableEditor = Backbone.View.extend({
  rowEditor: undefined,   /* Must be overriden by subclasses */

  initialize: function() {
    _.bindAll(this, "addOne", "removeOne", "render", "createRow");
    this.collection.bind("add", this.addOne);
    this.collection.bind("remove", this.removeOne);
    this.collection.bind("reset", this.render);

    var self = this;

    // create the row and then auto-focus the first input of the newly-added row
    this.$('>.add-link').click(function(e) {
      e.preventDefault();

      self.createRow();
      self.$("> table > tbody > tr:last-child > td:first-child input").first().focus();
    });

    this.templateName = this.$('table').data('template');
    if (!this.templateName) console.error("Missing template name", this);
    this.template = window.JST['settings/' + this.templateName];

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
    var row = $(this.template()).addClass('editable-row').appendTo(this.table).attr('data-row-id', added.cid);

    // Bind the model to the row
    var rowView = new this.rowEditor({el: row, model: added});
  },

  removeOne: function(removed) {
    this.$('[data-row-id=' + removed.cid + ']').remove();
  },

  createRow: function() {
    this.collection.add(new this.collection.model({}, {collection: this.collection}));
  },

  close: function() {
    this.undelegateEvents();

    this.collection.unbind("add", this.addOne);
    this.collection.unbind("reset", this.render);
  }
});
Diffa.Views.RowEditor = Backbone.View.extend({
  remove: function() {
    this.model.collection.remove(this.model);
  }
})
Diffa.Views.CategoryEditor = Diffa.Views.RowEditor.extend({
  events: {
    "click .remove-category": 'remove'
  },
  initialize: function() {
    this.viewBinding = rivets.bind(this.el, {category: this.model});

    new Diffa.Binders.ListBinder(this, "data-el-list-key");
  }
});
Diffa.Views.CategoriesEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.CategoryEditor
});
Diffa.Views.PairViewEditor = Diffa.Views.RowEditor.extend({
  events: {
    "click .remove-view": 'remove'
  },
  initialize: function() {
    this.viewBinding = rivets.bind(this.el, {view: this.model});
  }
});
Diffa.Views.PairViewsEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.PairViewEditor
});
Diffa.Views.EndpointViewEditor = Diffa.Views.RowEditor.extend({
  events: {
    "click .remove-view": 'remove'
  },
  initialize: function() {
    this.viewBinding = rivets.bind(this.el, {view: this.model});
    
    this.categoryEditors = [
      new Diffa.Views.CategoriesEditor({collection: this.model.rangeCategories, el: this.$('.range-categories')}),
      new Diffa.Views.CategoriesEditor({collection: this.model.setCategories, el: this.$('.set-categories')}),
      new Diffa.Views.CategoriesEditor({collection: this.model.prefixCategories, el: this.$('.prefix-categories')})
    ];
  }
});
Diffa.Views.EndpointViewsEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.EndpointViewEditor
});
Diffa.Views.RepairActionEditor = Diffa.Views.RowEditor.extend({
  events: {
    "click .remove-action": 'remove'
  },
  initialize: function() {
    this.viewBinding = rivets.bind(this.el, {action: this.model});
  }
});
Diffa.Views.RepairActionsEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.RepairActionEditor
});
Diffa.Views.ReportEditor = Diffa.Views.RowEditor.extend({
  events: {
    "click .remove-report": 'remove'
  },
  initialize: function() {
    this.viewBinding = rivets.bind(this.el, {report: this.model});
  }
});
Diffa.Views.ReportsEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.ReportEditor
});
Diffa.Views.EscalationEditor = Diffa.Views.RowEditor.extend({
  events: {
    "click .remove-escalation": 'remove'
  },
  initialize: function() {
    this.viewBinding = rivets.bind(this.el, {escalation: this.model});
  }
});
Diffa.Views.EscalationsEditor = Diffa.Views.TableEditor.extend({
  rowEditor: Diffa.Views.EscalationEditor
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

    $(el).children().last().focus();

    $('<button>+</button>').
      appendTo(el).
      click(function() {
        self.view.model.set(attrName, (self.view.model.get(attrName) || []).concat(""))
      });
  }
});

Diffa.Helpers.bindList = function(el, templateName, collectionName, elementType) {
  var domain = Diffa.DomainManager.get($(el).data('domain'));

  var template = window.JST[templateName];

  new Diffa.Views.ElementList({
    el: $(el).html(template({API_BASE: API_BASE})),
    collection: domain[collectionName],
    elementType: elementType
  });
};

Diffa.Helpers.maybeAttachView = function(el, params, viewBuilder) {
  if (el.lastParams && el.lastParams == params) return el.view;

  if (el.view) {
    el.view.close();
  }
  
  el.view = viewBuilder();
  el.lastParams = params;

  return el.view;
};
Diffa.Helpers.maybeDetachView = function(el) {
  if (el.view) {
    el.view.close();
    delete el.view;
    delete el.lastParams;
  }
};

Diffa.Helpers.bindEditor = function(el, elementType, collectionName, modelClass, editorClass, initNewElHelper) {
  var refresh = function() {
    var domain = Diffa.DomainManager.get($(el).data('domain'));
    var elName = $(el).data(elementType);
    var pane = $(el).data('pane');
    
    if (elName === undefined) {
      Diffa.Helpers.maybeDetachView(el);
    } else {
      var viewConfig = {domain: domain.id};
      viewConfig[elementType] = elName;

      var view = Diffa.Helpers.maybeAttachView(el, viewConfig, function() {
        var model;
        if (elName != "") {
          model = domain[collectionName].get(elName)
          model.isSaved = true;
        } else {
          model = new modelClass();
          model.domain = domain;
          model.isSaved = false;

          if (initNewElHelper) initNewElHelper(model, domain);
        }

        return new editorClass({
          model: model,
          collection: domain[collectionName],
          el: el
        });
      });
      view.setPane(pane);
    }
  };

  $(el).on('changed:' + elementType, function() { refresh(); });
  refresh();
};

Diffa.Helpers.ChangeMonitor = {
  monitorInputsForChanges: function(el) {
    $(el).delegate('input', 'focus', function() {
      var inputEl = $(this);
      var currentVal = inputEl.val();
      var monitor = window.setInterval(function() {
        var newVal = inputEl.val();
        if (newVal != currentVal) {
          // Triggering the event with jQuery doesn't result in Rivets being activated. Generate a native
          // change event instead.
          if(inputEl[0].fireEvent) {
            inputEl[0].fireEvent("onchange");
          } else {
            var ev = document.createEvent('HTMLEvents');
            ev.initEvent("change", true, false);
            inputEl[0].dispatchEvent(ev);
          }
        }
      }, 1000);

      var blurHandler = function() {
        if (monitor) {
          window.clearInterval(monitor);
          monitor = null;

          $(inputEl).unbind('blur', blurHandler);
        }
      };

      $(inputEl).blur(blurHandler);
    });
  }
}

rivets.configure({
  prefix: 'bind',

  adapter: {
    subscribe: function(obj, keypath, callback) {
      if (keypath) {
        callback.wrapped = function(m, v) { callback(v) };
        obj.on('change:' + keypath, callback.wrapped);
      } else {
        callback.wrapped = function(m, v) { callback(m) };
        obj.on('change', callback.wrapped);
      }
    },
    unsubscribe: function(obj, keypath, callback) {
      obj.off('change' + (keypath ? ':' + keypath : ''), callback.wrapped);
    },
    read: function(obj, keypath) {
      if (keypath == 'dirty') {
        return obj.dirty;
      } else if (keypath) {
        return obj.get(keypath);
      } else {
        return obj;
      }
    },
    publish: function(obj, keypath, value) {
      obj.set(keypath, value);
    }
  }
});

$('.diffa-element-selector').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Views.ElementSelector({domain: domain, el: $(this)});
});
$('.diffa-endpoint-list').each(function() {
  Diffa.Helpers.bindList(this, 'settings/endpointlist', 'endpoints', 'endpoint');
});
$('.diffa-pair-list').each(function() {
  Diffa.Helpers.bindList(this, 'settings/pairlist', 'pairs', 'pair');
});
$('.diffa-settings-page').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));
  new Diffa.Routers.Config({domain: domain, el: $(this)});

  domain.loadAll(['endpoints', 'pairs'], function() {
    Backbone.history.start();
  });
});
$('.diffa-endpoint-editor').each(function() {
  Diffa.Helpers.bindEditor(this, "endpoint", "endpoints", Diffa.Models.Endpoint, Diffa.Views.EndpointEditor);
});
$('.diffa-pair-editor').each(function() {
  Diffa.Helpers.bindEditor(this, "pair", "pairs", Diffa.Models.Pair, Diffa.Views.PairEditor, function(model, domain) {
    // If we've got at least two endpoints, then try to make the endpoint selections sensible
    if (domain.endpoints.length >= 2) {
      model.set({upstreamName: domain.endpoints.at(0).id, downstreamName: domain.endpoints.at(1).id});
    }
  });
});
$('.diffa-settings-crumbs').each(function() {
  new Diffa.Views.SettingsBreadcrumb({el: this});
});

});