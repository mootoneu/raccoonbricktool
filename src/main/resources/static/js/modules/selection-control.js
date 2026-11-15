/**
 * Reusable table selection control.
 *
 * Owns checkbox selection state for one table: row checkboxes, the header
 * "select all visible" checkbox, the selected count, select-all and clear
 * buttons. Table-specific actions (bulk update, delete, etc.) stay in the
 * module that creates the control, through the onChange callback.
 */
(function () {
  'use strict';

  if (typeof BrickTools === 'undefined') {
    window.BrickTools = {};
  }

  /**
   * Normalize an id coming from a DOM attribute or row object.
   * @param {*} id - Value to normalize
   * @returns {string} Normalized id, or an empty string
   */
  function normalizeId(id) {
    return String(id == null ? '' : id).trim();
  }

  /**
   * Create a selection control for one table.
   *
   * Supported options:
   * - table: table selector or jQuery object
   * - toolbar: toolbar container selector or jQuery object
   * - rowSelector: row selector inside the table (default "tbody tr")
   * - rowIdAttr: data attribute that identifies a row and its checkbox
   * - checkboxSelector: row checkbox selector (default ".row-select")
   * - headerCheckbox: header "select all visible" checkbox
   * - countLabel: element showing the selected count
   * - selectAllButton: "Select all visible" button
   * - clearButton: "Clear selection" button
   * - itemLabel: singular noun used in the count (default "item")
   * - itemLabelPlural: plural noun used in the count
   * - onChange: callback(ids, info) called after the selection changes
   *
   * @param {Object} options - Configuration options
   * @returns {Object|null} Selection control API, or null when the table is absent
   */
  function create(options) {
    const config = options || {};
    const $table = $(config.table);
    if (!$table.length) {
      return null;
    }

    const $toolbar = $(config.toolbar);
    const $headerCheckbox = $(config.headerCheckbox);
    const $countLabel = $(config.countLabel);
    const $selectAllButton = $(config.selectAllButton);
    const $clearButton = $(config.clearButton);
    const rowSelector = config.rowSelector || 'tbody tr';
    const idAttr = config.rowIdAttr || 'data-row-id';
    const checkboxSelector = config.checkboxSelector || '.row-select';
    const itemLabel = config.itemLabel || 'item';
    const itemLabelPlural = config.itemLabelPlural || itemLabel + 's';
    const selectedIds = new Set();
    let visibleIds = [];

    /**
     * Read the id carried by a row or checkbox.
     * @param {jQuery} $element - Row or checkbox
     * @returns {string} Normalized id
     */
    function idOf($element) {
      return normalizeId($element.attr(idAttr));
    }

    /**
     * Return a copy of the selected ids.
     * @returns {string[]} Selected ids
     */
    function getSelectedIds() {
      return Array.from(selectedIds);
    }

    /**
     * Count how many visible ids are currently selected.
     * @returns {number} Number of selected visible ids
     */
    function selectedVisibleCount() {
      let count = 0;
      visibleIds.forEach(function (id) {
        if (selectedIds.has(id)) {
          count++;
        }
      });
      return count;
    }

    /**
     * Notify the owning module that the selection changed.
     */
    function notifyChange() {
      if (typeof config.onChange !== 'function') {
        return;
      }
      config.onChange(getSelectedIds(), {
        visibleIds: visibleIds.slice(),
        selectedVisibleCount: selectedVisibleCount()
      });
    }

    /**
     * Apply the current selection state to the toolbar, header and rows.
     */
    function refresh() {
      const count = selectedIds.size;
      const visibleCount = visibleIds.length;
      const selectedVisible = selectedVisibleCount();

      if ($toolbar.length) {
        $toolbar.prop('hidden', visibleCount === 0 && count === 0);
      }
      if ($countLabel.length) {
        const label = count === 1 ? itemLabel : itemLabelPlural;
        $countLabel.text(count + ' ' + label + ' selected');
      }
      if ($clearButton.length) {
        $clearButton.prop('disabled', count === 0);
      }
      if ($selectAllButton.length) {
        $selectAllButton.prop('disabled', visibleCount === 0);
      }
      if ($headerCheckbox.length) {
        $headerCheckbox
          .prop('disabled', visibleCount === 0)
          .prop('checked', visibleCount > 0 && selectedVisible === visibleCount)
          .prop('indeterminate', selectedVisible > 0 && selectedVisible < visibleCount);
      }

      $table.find(rowSelector).each(function () {
        const $row = $(this);
        const id = idOf($row);
        const selected = id !== '' && selectedIds.has(id);
        $row.toggleClass('row-selected', selected);
        $row.find(checkboxSelector).prop('checked', selected);
      });
    }

    /**
     * Add every visible id to the selection.
     */
    function selectAllVisible() {
      visibleIds.forEach(function (id) {
        selectedIds.add(id);
      });
      refresh();
      notifyChange();
    }

    /**
     * Remove the visible ids from the selection without touching hidden ids.
     */
    function deselectAllVisible() {
      visibleIds.forEach(function (id) {
        selectedIds.delete(id);
      });
      refresh();
      notifyChange();
    }

    /**
     * Clear every selected id, visible or not.
     */
    function clear() {
      selectedIds.clear();
      refresh();
      notifyChange();
    }

    /**
     * Update the ids currently visible after filtering and sorting.
     * @param {Array<*>} ids - Visible row ids
     */
    function setVisibleIds(ids) {
      visibleIds = (ids || []).map(normalizeId).filter(function (id) {
        return id !== '';
      });
      refresh();
    }

    /**
     * Remove the control's event handlers.
     */
    function destroy() {
      $table.off('.selectionControl');
      $headerCheckbox.off('.selectionControl');
      $selectAllButton.off('.selectionControl');
      $clearButton.off('.selectionControl');
    }

    $table.on('change.selectionControl', checkboxSelector, function () {
      const id = idOf($(this));
      if (!id) {
        return;
      }
      if (this.checked) {
        selectedIds.add(id);
      } else {
        selectedIds.delete(id);
      }
      refresh();
      notifyChange();
    });

    $headerCheckbox.on('change.selectionControl', function () {
      if (this.checked) {
        selectAllVisible();
        return;
      }
      deselectAllVisible();
    });

    $selectAllButton.on('click.selectionControl', function (event) {
      event.preventDefault();
      selectAllVisible();
    });

    $clearButton.on('click.selectionControl', function (event) {
      event.preventDefault();
      clear();
    });

    refresh();

    return {
      setVisibleIds: setVisibleIds,
      getSelectedIds: getSelectedIds,
      clear: clear,
      refresh: refresh,
      destroy: destroy
    };
  }

  BrickTools.SelectionControl = {
    create: create
  };
})();
