/**
 * Parts Collection - Shared Utilities Module
 * Collection-specific helpers; generic table/color helpers live in shared/table.js.
 */
(function () {
  'use strict';

  const ORDER_URL = 'https://www.brickowl.com/user/934717/orders/';

  if (typeof BrickTools === 'undefined') {
    window.BrickTools = {};
  }

  const shared = BrickTools.Shared;

  /**
   * Get the sort value for a row. The Orders column is sorted by the number of
   * orders containing the part rather than by comparing the order objects.
   */
  function sortValue(row, key) {
    if (key === 'orders') {
      return Array.isArray(row.orders) ? row.orders.length : 0;
    }
    return row[key];
  }

  function sortRows(rows, state) {
    return shared.sortRows(rows, state, sortValue);
  }

  function orderIdOf(order) {
    return order && order.id != null ? order.id : order;
  }

  function orderCount(rows) {
    const ids = new Set();
    rows.forEach(function (part) {
      (part.orders || []).forEach(function (order) {
        const id = orderIdOf(order);
        if (id != null && id !== '') {
          ids.add(String(id));
        }
      });
    });
    return ids.size;
  }

  function renderColorCell(part) {
    return shared.renderColorCell({
      colorId: part.colorId,
      colorName: part.colorName,
      source: 'BO'
    });
  }

  /** Render a cell with links to the BrickOwl orders containing a part. */
  function renderOrdersCell(orders) {
    const $cell = $('<td></td>');
    const list = Array.isArray(orders) ? orders : [];
    if (list.length === 0) {
      $cell.text('—');
      return $cell;
    }
    list.forEach(function (order, index) {
      if (index > 0) {
        $cell.append(document.createTextNode(', '));
      }
      const id = String(orderIdOf(order));
      const details = order && typeof order === 'object'
        ? [order.orderTime, order.storeName].filter(Boolean).join(' · ')
        : '';
      const $link = $('<a></a>')
        .attr('href', ORDER_URL + encodeURIComponent(id))
        .attr('target', '_blank')
        .attr('rel', 'noopener noreferrer')
        .text(id);
      if (details) {
        $link.attr('title', details);
      }
      $cell.append($link);
    });
    return $cell;
  }

  BrickTools.PartsCollectionShared = {
    ORDER_URL: ORDER_URL,
    compareValues: shared.compareValues,
    sortValue: sortValue,
    sortRows: sortRows,
    sortBy: shared.sortBy,
    markSortedHeader: shared.markSortedHeader,
    report: shared.report,
    orderIdOf: orderIdOf,
    orderCount: orderCount,
    readableTextColor: shared.readableTextColor,
    colorHex: shared.colorHex,
    renderColorCell: renderColorCell,
    renderOrdersCell: renderOrdersCell
  };
})();
