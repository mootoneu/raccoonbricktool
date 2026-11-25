/**
 * BrickOwl - Shared Utilities Module
 * BrickOwl-specific state and constants; generic table/color helpers live in shared/table.js.
 */
(function () {
  'use strict';

  if (typeof BrickTools === 'undefined') {
    window.BrickTools = {};
  }

  const shared = BrickTools.Shared;
  const fmt = BrickTools.fmt;
  const esc = BrickTools.esc;

  /** Metrics for statistics display. */
  const METRICS = [
    { key: 'amount', label: 'Amount', format: fmt.money },
    { key: 'pieces', label: 'Pieces', format: fmt.number },
    { key: 'pricePerPiece', label: 'Price per piece', format: fmt.money },
    { key: 'pricePerKg', label: 'Price per kg', format: fmt.money },
    { key: 'minifigs', label: 'Minifigs', format: fmt.number },
    { key: 'sets', label: 'Sets', format: fmt.number }
  ];

  let orders = [];
  let items = [];
  const sortState = {
    orders: { key: null, dir: 1 },
    items: { key: null, dir: 1 }
  };

  function renderColorCell(item) {
    return shared.renderColorCell({
      colorId: item.ColorId,
      colorName: item.ColorName,
      source: 'BO'
    });
  }

  BrickTools.BrickOwlShared = {
    METRICS: METRICS,
    fmt: fmt,
    esc: esc,

    getOrders: function () { return orders; },
    setOrders: function (data) { orders = data; },
    getItems: function () { return items; },
    setItems: function (data) { items = data; },
    getSortState: function () { return sortState; },

    compareValues: shared.compareValues,
    sortRows: shared.sortRows,
    sortBy: shared.sortBy,
    markSortedHeader: shared.markSortedHeader,
    report: shared.report,
    readableTextColor: shared.readableTextColor,
    colorHex: shared.colorHex,
    renderColorCell: renderColorCell
  };
})();
