/**
 * Parts Collection - Drawer rendering module.
 * State is passed through a context object so this module does not close over the storage module's variables.
 */
(function () {
  'use strict';

  function drawerPartList(parts) {
    const $list = $('<div class="drawer-parts"></div>');
    parts.forEach(function (part) {
      const $item = $('<span class="drawer-part"></span>');
      const colorId = part.colorId == null || part.colorId === '' ? '' : part.colorId;
      const src = '/api/parts/' + encodeURIComponent(part.partNumber) + '/image'
        + (colorId === '' ? '' : '?colorId=' + encodeURIComponent(colorId));
      $item.append($('<img class="part-img" loading="lazy" alt="">')
        .attr('src', src)
        .attr('alt', part.name || part.partNumber)
        .attr('title', part.name || part.partNumber)
        .attr('data-part-number', part.partNumber)
        .attr('tabindex', '0')
        .attr('role', 'button')
        .attr('aria-label', 'Part information for ' + (part.name || part.partNumber))
        .on('error', function () { $(this).prop('hidden', true); }));
      $item.append($('<span class="drawer-part-name"></span>').text(part.name || part.partNumber));
      $list.append($item);
    });
    return $list;
  }

  function drawerRule(row, card) {
    const $rule = $('<div class="drawer-rule"></div>');
    const $head = $('<div class="drawer-rule-head"></div>');
    $head.append($('<span class="drawer-amount"></span>').text('Amount: ' + (row.amount || '—')));
    const $actions = $('<div class="drawer-rule-actions"></div>');
    $actions.append(BrickTools.iconButton('edit', 'Edit content rule', 'secondary drawer-rule-edit')
      .attr('data-index', row.index)
      .attr('data-storage-id', card.storageId)
      .attr('data-drawer', card.drawer));
    $actions.append(BrickTools.iconButton('delete', 'Delete content rule', 'danger drawer-rule-delete')
      .attr('data-index', row.index));
    $head.append($actions);
    $rule.append($head);

    const partChips = Array.isArray(row.partChips) ? row.partChips : [];
    if (partChips.length > 0) {
      $rule.append(drawerPartList(partChips));
    }

    const $chips = $('<div class="drawer-chips"></div>');
    (row.categoryChips || []).forEach(function (category) {
      $chips.append($('<span class="drawer-chip category"></span>').text('§ ' + (category.name || category.value)));
    });
    (row.tagChips || []).forEach(function (tag) {
      $chips.append($('<span class="drawer-chip tag"></span>').text('#' + tag));
    });
    (row.colorChips || []).forEach(function (color) {
      const $chip = $('<span class="drawer-chip"></span>').text(color.name || '');
      if (color.keyword) {
        $chip.addClass('keyword');
      } else {
        const hex = BrickTools.PartsCollectionShared.colorHex({ rgb: color.rgb });
        if (hex) {
          $chip.css({
            'background-color': hex,
            color: BrickTools.PartsCollectionShared.readableTextColor(hex)
          });
        }
      }
      $chips.append($chip);
    });
    if ($chips.children().length > 0) {
      $rule.append($chips);
    }
    return $rule;
  }

  function renderCards($host, cards, searching) {
    $host.empty();
    if (cards.length === 0) {
      return;
    }
    const active = BrickTools.getActiveContainer();
    cards.forEach(function (card) {
      const isActive = active && active.type === 'drawer'
        && String(active.storageId) === String(card.storageId)
        && String(active.drawer) === String(card.drawer);
      const $card = $('<article class="drawer-card"></article>').toggleClass('active', Boolean(isActive));

      const $head = $('<div class="drawer-card-head"></div>');
      const $titles = $('<div></div>');
      $titles.append($('<h3 class="drawer-card-title"></h3>').text(
        (card.storageId || '') + ' · ' + (card.drawer || '—')));
      const sub = [card.storageModel, card.storageLocation].filter(Boolean).join(' · ');
      if (sub) {
        $titles.append($('<p class="drawer-card-sub"></p>').text(sub));
      }
      $head.append($titles);

      const $actions = $('<div class="drawer-card-actions"></div>');
      $actions.append(BrickTools.iconButton('add', 'Add content to drawer ' + (card.drawer || ''), 'secondary drawer-add-content')
        .attr('data-storage-id', card.storageId)
        .attr('data-drawer', card.drawer));
      $actions.append(BrickTools.iconButton('active', 'Set drawer ' + (card.drawer || '') + ' as active', 'secondary drawer-set-active')
        .attr('data-storage-id', card.storageId)
        .attr('data-drawer', card.drawer));
      $head.append($actions);
      $card.append($head);

      const $content = $('<div class="drawer-content"></div>');
      const rows = Array.isArray(card.rows) ? card.rows : [];
      if (rows.length === 0) {
        $content.append($('<p class="drawer-empty"></p>').text('No content rule for this drawer.'));
      }
      rows.forEach(function (row) {
        $content.append(drawerRule(row, card));
      });
      $card.append($content);

      const matched = Array.isArray(card.matchedParts) ? card.matchedParts : [];
      if (searching && matched.length > 0) {
        const $matched = $('<div class="drawer-content"></div>');
        $matched.append($('<p class="drawer-card-sub"></p>').text(
          matched.length + ' matching part(s)'));
        $matched.append(drawerPartList(matched));
        $card.append($matched);
      }
      $host.append($card);
    });
  }

  function list(cards) {
    return cards.map(function (card) {
      return {
        storageId: card.storageId,
        storageModel: card.storageModel,
        storageLocation: card.storageLocation,
        drawer: card.drawer
      };
    });
  }

  function findDrawer(storageId, drawer, cards) {
    return cards.find(function (card) {
      return String(card.storageId) === String(storageId)
        && String(card.drawer) === String(drawer);
    });
  }

  function render(ctx) {
    const selectedId = String(ctx.storageId || $('#drawers-storage').val() || '');
    const cards = ctx.drawerCards.filter(function (card) {
      return !selectedId || String(card.storageId) === selectedId;
    });
    const filter = String(ctx.filter || '').trim().toLowerCase();
    const visible = cards.filter(function (card) {
      if (!filter) return true;
      return [card.storageId, card.storageModel, card.storageLocation, card.drawer]
        .some(function (value) {
          return String(value || '').toLowerCase().indexOf(filter) !== -1;
        });
    });
    $('#drawers-count').text(cards.length === 0 ? '' :
      BrickTools.fmt.number(visible.length) + ' of ' + BrickTools.fmt.number(cards.length)
        + ' drawer(s)');
    renderCards($('#drawer-cards'), visible, false);
    if (ctx.storages.length === 0) {
      ctx.report('No drawer yet. Add a part storage first.');
    } else if (!selectedId) {
      ctx.report('Select a storage.');
    } else if (visible.length === 0 && cards.length > 0) {
      ctx.report('No drawer matches the filter.');
    } else if (cards.length === 0) {
      ctx.report('No drawer for this storage.');
    } else {
      ctx.report('');
    }
  }

  function populateSelect(ctx) {
    const $select = $('#drawers-storage');
    const current = String(ctx.storageId || $select.val() || '');
    $select.empty();
    if (ctx.storages.length === 0) {
      ctx.storageId = '';
      $select.append($('<option></option>').attr('value', '').text('No storage'));
      $select.prop('disabled', true);
      return;
    }
    $select.prop('disabled', false);
    ctx.storages.forEach(function (storage) {
      const label = storage.model ? storage.id + ' (' + storage.model + ')' : storage.id;
      $select.append($('<option></option>').attr('value', storage.id).text(label));
    });
    const selected = ctx.storages.find(function (storage) {
      return String(storage.id) === current;
    });
    ctx.storageId = selected ? String(selected.id) : String(ctx.storages[0].id);
    $select.val(ctx.storageId);
  }

  function setActive(card, ctx) {
    BrickTools.setActiveContainer({
      type: 'drawer',
      storageId: card.storageId,
      storageModel: card.storageModel,
      storageLocation: card.storageLocation,
      drawer: card.drawer,
      drawers: list(ctx.drawerCards)
    });
    render(ctx);
    if (!ctx.loaded && !ctx.loading) {
      // The header navigation needs the full drawer list of the storage.
      ctx.load();
    }
    ctx.report('Active container set to storage ' + card.storageId + ', drawer ' + (card.drawer || '—') + '.');
  }

  function syncActive(cards) {
    const active = BrickTools.getActiveContainer();
    if (!active || active.type !== 'drawer') return;
    const drawers = list(cards);
    const found = drawers.some(function (drawer) {
      return String(drawer.storageId) === String(active.storageId)
        && String(drawer.drawer) === String(active.drawer);
    });
    if (found) {
      active.drawers = drawers;
      BrickTools.setActiveContainer(active);
    }
  }

  BrickTools.PartsCollectionDrawers = {
    renderCards: renderCards,
    render: render,
    populateSelect: populateSelect,
    setActive: setActive,
    syncActive: syncActive,
    list: list,
    findDrawer: findDrawer
  };
})();
