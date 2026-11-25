/**
 * Shared table and color helpers used by the BrickOwl and Parts Collection modules.
 * Loaded after common.js/color.js and before the page modules that expose BrickTools.*Shared.
 */
(function () {
  'use strict';

  if (typeof BrickTools === 'undefined') {
    window.BrickTools = {};
  }

  /**
   * Compare two values for sorting, with numeric-aware string comparison.
   * @param {*} a first value
   * @param {*} b second value
   * @param {number} dir 1 for ascending, -1 for descending
   * @returns {number} comparison result
   */
  function compareValues(a, b, dir) {
    const x = a == null ? '' : String(a);
    const y = b == null ? '' : String(b);
    if (x === '' && y === '') return 0;
    if (x === '') return 1;
    if (y === '') return -1;
    const nx = Number(x), ny = Number(y);
    if (!isNaN(nx) && !isNaN(ny)) return (nx - ny) * dir;
    return x.localeCompare(y, undefined, { numeric: true }) * dir;
  }

  /**
   * Sort a copy of the rows using the state's key and direction.
   * @param {Array} rows array of row objects
   * @param {Object} state object with key and dir properties
   * @param {Function} [valueOf] optional accessor used to get the sort value of a row
   * @returns {Array} sorted rows
   */
  function sortRows(rows, state, valueOf) {
    if (!state.key) return rows;
    const value = valueOf || function (row, key) { return row[key]; };
    return rows.slice().sort(function (a, b) {
      return compareValues(value(a, state.key), value(b, state.key), state.dir);
    });
  }

  function sortBy(state, key) {
    state.dir = state.key === key ? -state.dir : 1;
    state.key = key;
  }

  function markSortedHeader(table, state) {
    $(table).find('thead th[data-key]').removeClass('sort-asc sort-desc')
      .filter('[data-key="' + state.key + '"]')
      .addClass(state.dir > 0 ? 'sort-asc' : 'sort-desc');
  }

  function report(selector, text) {
    $(selector).text(text);
  }

  /** Determine readable text color (dark or white) for a background hex color. */
  function readableTextColor(hex) {
    const full = String(hex || '#e5e7eb').replace('#', '');
    const expanded = full.length === 3
      ? full.split('').map(function (c) { return c + c; }).join('')
      : full.slice(0, 6);
    if (!/^[0-9a-fA-F]{6}$/.test(expanded)) return '#111827';
    const r = parseInt(expanded.slice(0, 2), 16);
    const g = parseInt(expanded.slice(2, 4), 16);
    const b = parseInt(expanded.slice(4, 6), 16);
    return (0.299 * r + 0.587 * g + 0.114 * b) > 150 ? '#111827' : '#ffffff';
  }

  /** Normalize a color RGB value to a hex string, or null when invalid. */
  function colorHex(color) {
    const rgb = String((color && color.rgb) || '').replace('#', '').trim();
    if (/^[0-9a-fA-F]{6}$/.test(rgb)) return '#' + rgb;
    if (/^[0-9a-fA-F]{3}$/.test(rgb)) {
      return '#' + rgb.split('').map(function (c) { return c + c; }).join('');
    }
    return null;
  }

  /**
   * Render a color chip inside a table cell. One implementation is shared by the
   * BrickOwl and Parts Collection tables so styling and keyboard behavior match.
   * @param {Object} options colorId, colorName, source ('BO' or 'LEGO') and optional openInfo flag
   * @returns {jQuery} TD element
   */
  function renderColorCell(options) {
    const settings = options || {};
    const colorId = String(settings.colorId || '').trim();
    const colorName = settings.colorName || '';
    const source = settings.source || 'BO';
    const openInfo = settings.openInfo !== false;
    const $cell = $('<td></td>');
    if (!colorId) {
      $cell.text(colorName);
      return $cell;
    }

    const $chip = $('<span class="color-chip"></span>')
      .attr('title', colorName)
      .attr('role', 'button')
      .attr('tabindex', '0')
      .text(colorName);
    $cell.append($chip);

    function resolveColor(callback) {
      BrickColor.getLegoColorId(source, colorId).then(function (legoId) {
        if (legoId === null || legoId === undefined) return;
        BrickColor.getColor(legoId).done(function (color) {
          callback(legoId, color);
        });
      });
    }

    resolveColor(function (legoId, color) {
      const hex = colorHex(color);
      if (hex) {
        $chip.css({ 'background-color': hex, color: readableTextColor(hex) });
      }
    });

    function open() {
      if (!openInfo) return;
      resolveColor(function (legoId) {
        BrickColor.openColorInfo(legoId);
      });
    }

    $chip.on('click', open);
    $chip.on('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        open();
      }
    });
    return $cell;
  }

  BrickTools.Shared = {
    compareValues: compareValues,
    sortRows: sortRows,
    sortBy: sortBy,
    markSortedHeader: markSortedHeader,
    report: report,
    readableTextColor: readableTextColor,
    colorHex: colorHex,
    renderColorCell: renderColorCell
  };
})();
