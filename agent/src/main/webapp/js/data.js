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
  setListColumnThreshold: 10,
  templates: {
    rangeConstraint: _.template('<div class="category" data-constraint="<%= name %>" data-constraint-type="<%= dataType %>">' +
                      '<h5 class="name"><%= name %> (<%= dataType %> range)</h5>' +
                      '<div>' +
                        '<label for="<%= name %>_range_start">Start:</label>' +
                        '<span class="clearable_input">' +
                          '<input id="<%= name %>_range_start" type="text" name="start" placeholder="<%= placeholder %>">' +
                          '<span class="clear"></span>' +
                        '</span>' +
                      '</div>' +
                      '<div>' +
                        '<label for="<%= name %>_range_end">End:</label>' +
                        '<span class="clearable_input">' +
                          '<input id="<%= name %>_range_end" type="text" name="end" placeholder="<%= placeholder %>">' +
                          '<span class="clear"></span>' +
                        '</span>' +
                      '</div>' +
                     '</div>'),
    prefixConstraint: _.template('<div class="category" data-constraint="<%= name %>">' +
                        '<h5 class="name"><%= name %> (prefix)</h5>' +
                        '<input type="text" name="prefix">' +
                      '</div>'),
    setConstraint: _.template('<div class="category" data-constraint="<%= name %>">' +
                        '<h5 class="name"><%= name %> (set)</h5>' +
                        '<% _.each(values, function(value) { %>' +
                          '<input type="checkbox" value="<%= value %>" id="constraint_<%= name %>_<%= value %>">' +
                          '<label for="constraint_<%= name %>_<%= value %>"><%= value %></label>' +
                          '<br>' +
                        '<% }); %>' +
                      '</div>'),
    setConstraintWithColumns: _.template('<div class="category" data-constraint="<%= name %>">' +
                        '<h5 class="name"><%= name %> (set)</h5>' +
                        '<div class="column_1">' +
                        '<% _.each(valuesFirst, function(value) { %>' +
                          '<input type="checkbox" value="<%= value %>" id="constraint_<%= name %>_<%= value %>">' +
                          '<label for="constraint_<%= name %>_<%= value %>"><%= value %></label>' +
                          '<br>' +
                        '<% }); %>' +
                        '</div>' +
                        '<div class="column_2">' +
                        '<% _.each(valuesSecond, function(value) { %>' +
                          '<input type="checkbox" value="<%= value %>" id="constraint_<%= name %>_<%= value %>">' +
                          '<label for="constraint_<%= name %>_<%= value %>"><%= value %></label>' +
                          '<br>' +
                        '<% }); %>' +
                        '</div>' +
                      '</div>')
  },

  initialize: function() {
    var self = this;

    _.bindAll(this, "render", "addOne", "onEndpointListUpdate");

    this.collection.bind('reset', this.render);
    this.collection.bind('add', this.addOne);
    this.collection.bind('remove', this.onEndpointListUpdate);

    $(this.el).html(window.JST['data/inventory-upload'])
    this.delegateEvents(this.events);
    
    this.selectList = this.$('select[name=endpoint]');

    this.render();
    this.collection.ensureFetched();
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
      var templateVars = rangeCat.toJSON();
      var type = rangeCat.get("dataType");
      var placeholder = "";

      switch(type) {
        case "date":     placeholder = "Unbounded date";     break;
        case "datetime": placeholder = "Unbounded datetime"; break;
        case "int":      placeholder = "Unbounded";     break;
      }

      templateVars["placeholder"] = placeholder;

      constraints.append(self.templates.rangeConstraint(templateVars));

      // add date picking to the range inputs

      var lowerBound = rangeCat.get("lower");
      var upperBound = rangeCat.get("upper");

      if (lowerBound) {
        var allowOld = false;
        var startDate = new Date(lowerBound);
      } else {
        var allowOld = true;
        var startDate = new Date();
      }

      if (upperBound) {
        var endDate = new Date(upperBound);
      } else {
        var endDate = -1;
      }

      // if the category we're adding is a date range, apply datepickers
      if (type == "date") {
        $(".category:last-child input[type=text]").glDatePicker({
          allowOld: allowOld, // as far back as possible or not
          startDate: startDate,
          endDate: endDate, // latest selectable date, days since start date (int), or no limit (-1)
          selectedDate: -1, // default select date, or nothing set (-1)
          onChange: function(target, newDate) {
            var result, year, month, day;

            // helper to pad our month/day values if needed
            var pad = function(s) { s = s.toString(); if (s.length < 2) { s = "0" + s; }; return s };
            year = newDate.getFullYear();
            month = pad(newDate.getMonth() + 1);
            day = pad(newDate.getDate());
            result = year + "-" + month + "-" + day;

            target.val(result);
            target.change(); // fire the event as though we just typed it in
          }
        });
      }

      $(".category:last-child input").bind("change keydown focus blur", function() {
        if ($(this).val().length > 0) {
          $(this).addClass("nonempty");
        } else {
          $(this).removeClass("nonempty");
        }
      });

      $(".clearable_input .clear").click(function() {
        $(this).siblings("input").val("");
        $(this).siblings(".nonempty").removeClass("nonempty");
      });
    });

    selectedEndpoint.setCategories.each(function(setCat) {
      var values = setCat.get("values");
      var threshold = self.setListColumnThreshold; // only split the values array when its size is > this number
      var template = self.templates.setConstraint;

      if (values.length >= threshold) {
        var first, second;

        // cut the list of items into two, ensuring X = [A, B] has |A| >= |B|
        var splitPoint = Math.ceil(values.length/2);
        first = values.slice(0, splitPoint);
        second = values.slice(splitPoint);

        setCat.set("valuesFirst", first);
        setCat.set("valuesSecond", second);

        template = self.templates.setConstraintWithColumns;
      }

      constraints.append(template(setCat.toJSON()));
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
      global: false,        // Don't invoke global event handlers - we'll deal with errors here locally
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

$('.diffa-inventory-uploader').each(function() {
  var domain = Diffa.DomainManager.get($(this).data('domain'));

  new Diffa.Views.InventoryUploader({
    el: $(this),
    collection: domain.endpoints
  });
});
});