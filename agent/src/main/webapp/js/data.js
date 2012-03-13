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

Diffa.Views.InventoryUploader = Backbone.View.extend({
  events: {
    'submit form': 'uploadInventory',
    'change select[name=endpoint]': 'selectEndpoint'
  },
  templates: {
    rangeConstraint: _.template('<div class="category" data-constraint="<%= name %>">' +
                      '<span class="name"><%= name %> Range</span>' +
                      '<input type="text" name="start">' +
                      ' -> ' +
                      '<input type="text" name="end">' +
                     '</div>'),
    prefixConstraint: _.template('<div class="category" data-constraint="<%= name %>">' +
                        '<span class="name"><%= name %> Prefix</span>' +
                        '<input type="text" name="prefix">' +
                      '</div>'),
    setConstraint: _.template('<div class="category" data-constraint="<%= name %>">' +
                        '<span class="name"><%= name %> Values</span>' +
                        '<% _.each(values, function(value) { %>' +
                          '<input type="checkbox" value="<%= value %>" id="constraint_<%= name %>_<%= value %>">' +
                          '<label for="constraint_<%= name %>_<%= value %>"><%= value %></label>' +
                        '<% }); %>' +
                      '</div>')
  },

  initialize: function() {
    var self = this;

    _.bindAll(this, "render", "addOne", "onEndpointListUpdate");

    this.collection.bind('reset', this.render);
    this.collection.bind('add', this.addOne);
    this.collection.bind('remove', this.onEndpointListUpdate);

    this.selectList = this.$('select[name=endpoint]');

    this.render();
  },

  render: function() {
    var self = this;

    this.selectList.empty();
    this.collection.each(this.addOne);

    this.onEndpointListUpdate();

    return this;
  },

  addOne: function(e) {
    $('<option value="' + e.id + '">' + e.get('name') + '</option>').appendTo(this.selectList);
    this.onEndpointListUpdate();
  },

  onEndpointListUpdate: function() {
    this.selectEndpoint();    // Trigger an endpoint selection to ensure the attributes are shown
  },

  selectEndpoint: function(e) {
    var self = this;

    var selectedEndpoint = this.collection.get(this.selectList.val());
    if (selectedEndpoint && this.currentEndpoint && selectedEndpoint.id == this.currentEndpoint.id) return;

    this.currentEndpoint = selectedEndpoint;

    var constraints = this.$('.constraints');
    constraints.empty();

    if (!selectedEndpoint) return;

    selectedEndpoint.rangeCategories.each(function(rangeCat) {
      constraints.append(self.templates.rangeConstraint(rangeCat.toJSON()));
    });
    selectedEndpoint.setCategories.each(function(setCat) {
      constraints.append(self.templates.setConstraint(setCat.toJSON()));
    });
    selectedEndpoint.prefixCategories.each(function(prefixCat) {
      constraints.append(self.templates.prefixConstraint(prefixCat.toJSON()));
    });
  },

  calculateConstraints: function(e) {
    var result = [];
    var constraints = this.$('.constraints');
    var findContainer = function(name) { return constraints.find('[data-constraint=' + name + ']'); };

    var addConstraint = function(key, value) {
      result.push(encodeURIComponent(key) + "=" + encodeURIComponent(value));
    };

    this.currentEndpoint.rangeCategories.each(function(rangeCat) {
      var start = findContainer(rangeCat.get('name')).find('input[name=start]').val();
      var end = findContainer(rangeCat.get('name')).find('input[name=end]').val();

      if (start.length > 0) addConstraint(rangeCat.get('name') + "-start", start);
      if (end.length > 0) addConstraint(rangeCat.get('name') + "-end", end);
    });
    this.currentEndpoint.setCategories.each(function(setCat) {
      var selected = findContainer(setCat.get('name')).
        find('input[type=checkbox]:checked').
        each(function(idx, c) {
          addConstraint(setCat.get('name'), $(c).val());
        });
    });
    this.currentEndpoint.prefixCategories.each(function(prefixCat) {
      var prefix = findContainer(prefixCat.get('name')).find('input[name=prefix]').val();

      if (prefix.length > 0) addConstraint(prefixCat.get('name') + "-prefix", prefix);
    });

    return result.join('&');
  },

  uploadInventory: function(e) {
    e.preventDefault();

    var endpoint = this.collection.get(this.selectList.val());
    var inventoryFiles = this.$('input[type=file]')[0].files;
    if (inventoryFiles.length < 1) {
      alert("No inventory files selected for upload");
      return false;
    }
    var inventoryFile = inventoryFiles[0];

    var constraints = this.calculateConstraints();
    
    var submitButton = this.$('input[type=submit]');
    submitButton.attr('disabled', 'disabled');
    var statusPanel = this.$('.status');
    var applyStatus = function(text, clazz) {
      statusPanel.
        removeClass('info success error').
        addClass(clazz).
        text(text).
        show();
    };

    applyStatus('Uploading...', 'info');
    endpoint.uploadInventory(inventoryFile, constraints, {
      success: function() {
        submitButton.removeAttr('disabled');
        applyStatus('Inventory Submitted', 'success');
      },
      error: function(jqXHR, textStatus) {
        submitButton.removeAttr('disabled');

        var message = "Unknown Cause";
        if (jqXHR.status == 0) {
          message = textStatus;
        } else if (jqXHR.status == 400) {
          message = jqXHR.responseText;
        } else if (jqXHR.status == 403) {
          message = "Access Denied";
        } else {
          message = "Server Error (" + jxXHR.status + ")";
        }

        applyStatus('Inventory Submission Failed: ' + message, 'error');
      }
    });

    return false;
  }
});

Diffa.currentDomain = currentDiffaDomain;
Diffa.EndpointsCollection = new Diffa.Collections.Endpoints();
Diffa.InventoryUploaderView =  new Diffa.Views.InventoryUploader({
  el: $('#inventory-uploader'),
  collection: Diffa.EndpointsCollection
});

Diffa.EndpointsCollection.fetch();
});
