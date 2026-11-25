/**
 * Active container helpers (application header). Loaded after common.js, which provides the
 * storage, API and icon helpers used here.
 */
(function () {
  'use strict';

  const KEY = 'bricktools.activeContainer';
  const storageGet = BrickTools.storageGet;
  const storageSet = BrickTools.storageSet;
  const api = BrickTools.api;
  const icon = BrickTools.icon;

  function getActiveContainer() {
    const raw = storageGet(KEY);
    if (!raw) return null;
    try {
      const container = JSON.parse(raw);
      return container && container.type ? container : null;
    } catch (e) {
      return null;
    }
  }

  function activeContainerLabel(container) {
    if (!container) return '';
    switch (container.type) {
      case 'storage': return (container.storageId || '');
      case 'drawer':
        return (container.storageId || '') + '·'
          + (container.drawer === '' || container.drawer == null ? '—' : container.drawer);
      case 'pab': return 'PaB' + (container.purchaseId == null ? '' : container.purchaseId);
      case 'brickowl': return 'BO' + (container.orderId || '');
      default: return container.label || container.type;
    }
  }

  function activeContainerDetail(container) {
    if (!container) return '';
    if (container.type === 'drawer' || container.type === 'storage') {
      return [container.storageModel, container.storageLocation].filter(Boolean).join(' · ');
    }
    return container.detail || '';
  }

  function drawerListOf(container) {
    return container && Array.isArray(container.drawers) ? container.drawers : [];
  }

  function stepActiveDrawer(direction) {
    const container = getActiveContainer();
    if (!container || container.type !== 'drawer') return;
    const drawers = drawerListOf(container);
    const index = drawers.findIndex(function (drawer) {
      return String(drawer.storageId) === String(container.storageId)
        && String(drawer.drawer) === String(container.drawer);
    });
    const next = index + direction;
    if (index < 0 || next < 0 || next >= drawers.length) return;
    const target = drawers[next];
    setActiveContainer({
      type: 'drawer',
      storageId: target.storageId,
      storageModel: target.storageModel,
      storageLocation: target.storageLocation,
      drawer: target.drawer,
      drawers: drawers
    });
  }

  function renderActiveContainer() {
    const $host = $('#app-header .active-container');
    if (!$host.length) return;
    const container = getActiveContainer();
    $host.empty();
    if (!container) {
      $host.append($('<span class="active-container-empty"></span>').text('No active container'));
      return;
    }
    const drawers = drawerListOf(container);
    const index = drawers.findIndex(function (drawer) {
      return String(drawer.storageId) === String(container.storageId)
        && String(drawer.drawer) === String(container.drawer);
    });
    const label = activeContainerLabel(container);
    const detail = activeContainerDetail(container);

    if (container.type === 'drawer' && drawers.length > 1) {
      $host.append($('<button type="button" class="active-container-nav active-container-prev"></button>')
        .attr('title', 'Previous drawer')
        .attr('aria-label', 'Previous drawer')
        .prop('disabled', index <= 0)
        .html(icon('chevronLeft')));
    }
    const $label = $('<span class="active-container-label"></span>')
      .text(label)
      .attr('title', [label, detail].filter(Boolean).join(' — '));
    $host.append($label);
    if (container.type === 'drawer' && drawers.length > 1) {
      $host.append($('<button type="button" class="active-container-nav active-container-next"></button>')
        .attr('title', 'Next drawer')
        .attr('aria-label', 'Next drawer')
        .prop('disabled', index < 0 || index >= drawers.length - 1)
        .html(icon('chevronRight')));
    }
    $host.append($('<button type="button" class="active-container-clear"></button>')
      .attr('title', 'Clear active container')
      .attr('aria-label', 'Clear active container')
      .html(icon('close')));
  }

  function setActiveContainer(container) {
    storageSet(KEY, container ? JSON.stringify(container) : '');
    renderActiveContainer();
    $(document).trigger('bricktools:active-container-changed', [container || null]);
  }

  function addToActiveContainer(partNumbers) {
    const container = getActiveContainer();
    const numbers = (partNumbers || []).filter(function (partNumber) {
      return String(partNumber == null ? '' : partNumber).trim() !== '';
    });
    if (!container) {
      return $.Deferred().reject({
        responseJSON: { error: 'No active container. Set one from the Parts Collection page.' }
      }).promise();
    }
    if (numbers.length === 0) {
      return $.Deferred().reject({
        responseJSON: { error: 'No part selected.' }
      }).promise();
    }
    return api('POST', '/part-collection/add', {
      type: container.type,
      storageId: container.storageId,
      drawer: container.drawer,
      purchaseId: container.purchaseId,
      orderId: container.orderId,
      partNumbers: numbers
    }).done(function (result) {
      $(document).trigger('bricktools:container-parts-added', [container, result]);
    });
  }

  Object.assign(window.BrickTools, {
    getActiveContainer: getActiveContainer,
    setActiveContainer: setActiveContainer,
    addToActiveContainer: addToActiveContainer,
    activeContainerLabel: activeContainerLabel,
    renderActiveContainer: renderActiveContainer,
    stepActiveDrawer: stepActiveDrawer
  });
})();
