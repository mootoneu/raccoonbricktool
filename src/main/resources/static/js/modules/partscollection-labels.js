/**
 * Parts Collection - Label export module.
 * Builds the printable A4 drawer-label sheet. Data is passed in explicitly so this module does not
 * depend on the storage module's internal state.
 */
(function () {
  'use strict';

  const DEFAULT_LABEL_SIZE = { width: 60, height: 10 };
  const LABEL_SIZE_PATTERN = /^\s*(\d+(?:[.,]\d+)?)\s*[x\u00d7*]\s*(\d+(?:[.,]\d+)?)\s*$/i;

  /** Parse a storage LabelSize (`<width>x<height>` in millimeters), falling back to 60x10. */
  function parseLabelSize(value) {
    const match = LABEL_SIZE_PATTERN.exec(String(value == null ? '' : value));
    if (!match) {
      return { width: DEFAULT_LABEL_SIZE.width, height: DEFAULT_LABEL_SIZE.height };
    }
    const width = Number(match[1].replace(',', '.'));
    const height = Number(match[2].replace(',', '.'));
    if (!isFinite(width) || !isFinite(height) || width <= 0 || height <= 0) {
      return { width: DEFAULT_LABEL_SIZE.width, height: DEFAULT_LABEL_SIZE.height };
    }
    return { width: width, height: height };
  }

  function labelPartText(row) {
    const parts = [];
    (Array.isArray(row.partChips) ? row.partChips : []).forEach(function (part) {
      if (part.partNumber) {
        parts.push(String(part.partNumber));
      }
    });
    (Array.isArray(row.categoryChips) ? row.categoryChips : []).forEach(function (category) {
      parts.push('§ ' + (category.name || category.value || ''));
    });
    (Array.isArray(row.tagChips) ? row.tagChips : []).forEach(function (tag) {
      parts.push('#' + tag);
    });
    return parts.length > 0 ? parts.join(', ') : String(row.parts || '');
  }

  function labelColorText(row) {
    const names = (Array.isArray(row.colorChips) ? row.colorChips : [])
      .map(function (color) { return color.name || ''; })
      .filter(function (name) { return name !== ''; });
    return names.length > 0 ? names.join(', ') : String(row.colors || '');
  }

  function firstLabelPart(card) {
    const rows = Array.isArray(card.rows) ? card.rows : [];
    for (let i = 0; i < rows.length; i++) {
      const chips = Array.isArray(rows[i].partChips) ? rows[i].partChips : [];
      for (let j = 0; j < chips.length; j++) {
        if (chips[j].partNumber) {
          return chips[j];
        }
      }
    }
    return null;
  }

  function labelPartImageSrc(part) {
    const colorId = part.colorId == null || part.colorId === '' ? '' : part.colorId;
    return '/api/parts/' + encodeURIComponent(part.partNumber) + '/image'
      + (colorId === '' ? '' : '?colorId=' + encodeURIComponent(colorId));
  }

  function drawerLabelHtml(card, storage) {
    const esc = BrickTools.esc;
    const size = parseLabelSize(storage ? storage.labelSize : '');
    const fontSize = Math.round(Math.max(1.6, Math.min(3.4, size.height * 0.18)) * 100) / 100;
    const rows = Array.isArray(card.rows) ? card.rows : [];
    const part = firstLabelPart(card);
    let content = rows.map(function (row) {
      const meta = [labelColorText(row), row.amount].filter(Boolean).join(' · ');
      return '<div class="label-rule">'
        + '<span class="label-rule-parts">' + esc(labelPartText(row)) + '</span>'
        + (meta ? '<span class="label-rule-meta"> ' + esc(meta) + '</span>' : '')
        + '</div>';
    }).join('');
    if (!content) {
      content = '<span class="label-empty">Empty drawer</span>';
    }
    return '<div class="drawer-label" style="width:' + size.width + 'mm;height:' + size.height
      + 'mm;font-size:' + fontSize + 'mm">'
      + '<div class="label-image">'
      + (part ? '<img src="' + esc(labelPartImageSrc(part))
        + '" alt="" onerror="this.style.display=\'none\'">' : '')
      + '</div>'
      + '<div class="label-body">' + content + '</div>'
      + '<div class="label-id">'
      + '<span class="label-storage">' + esc(card.storageId || '') + '</span>'
      + (card.drawer ? '<span class="label-drawer">' + esc(card.drawer) + '</span>' : '')
      + '</div>'
      + '</div>';
  }

  function labelSheetCss() {
    return `
@page { size: A4 portrait; margin: 8mm; }
* { box-sizing: border-box; }
html, body { margin: 0; }
body { background: #f3f4f6; color: #111827;
  font-family: "Segoe UI", system-ui, -apple-system, sans-serif; }
.label-toolbar { display: flex; align-items: center; gap: 12px;
  padding: 10px 14px; background: #fff; border-bottom: 1px solid #d1d5db; font-size: 14px; }
.label-toolbar span { margin-right: auto; color: #4b5563; }
.label-toolbar button { font: inherit; padding: 6px 14px; border-radius: 6px;
  border: 1px solid #006cb7; background: #006cb7; color: #fff; cursor: pointer; }
.label-toolbar button.secondary { background: #fff; color: #006cb7; }
.label-sheet { display: flex; flex-wrap: wrap; align-content: flex-start;
  gap: 2mm; width: 194mm; margin: 8mm auto; }
.drawer-label { display: flex; align-items: stretch; overflow: hidden;
  border: .25mm solid #9ca3af; border-radius: .6mm; background: #fff;
  break-inside: avoid; page-break-inside: avoid; }
.label-image { flex: 0 0 30%; display: flex; align-items: center;
  justify-content: center; padding: .6mm; }
.label-image img { max-width: 100%; max-height: 100%; object-fit: contain; }
.label-body { flex: 0 0 50%; display: flex; flex-direction: column;
  justify-content: center; gap: .2mm; padding: .4mm .8mm; overflow: hidden; line-height: 1.15; }
.label-rule { overflow: hidden; overflow-wrap: anywhere; }
.label-rule-parts { font-weight: 600; }
.label-rule-meta { color: #4b5563; }
.label-empty, .label-empty-sheet { color: #6b7280; }
.label-empty-sheet { padding: 16px; }
.label-id { flex: 0 0 20%; display: flex; flex-direction: column;
  align-items: center; justify-content: center; padding: .3mm;
  border-left: .25mm solid #e5e7eb; overflow: hidden; }
.label-storage { font-size: 1.5em; font-weight: 700; line-height: 1.05;
  text-align: center; word-break: break-all; }
.label-drawer { font-size: .85em; color: #4b5563; line-height: 1; }
@media print {
  body { background: #fff; }
  .label-toolbar { display: none; }
  .label-sheet { width: auto; margin: 0; gap: 2mm; }
  .drawer-label { border: .25mm dashed #9ca3af; border-radius: 0; }
}`;
  }

  function writeLabelSheet(win, ids, storages, drawerCards) {
    const selected = new Set((ids || []).map(String));
    const entries = [];
    storages.forEach(function (storage) {
      if (!selected.has(String(storage.id))) {
        return;
      }
      drawerCards.forEach(function (card) {
        if (String(card.storageId) === String(storage.id)) {
          entries.push({ card: card, storage: storage });
        }
      });
    });

    const labels = entries.map(function (entry) {
      return drawerLabelHtml(entry.card, entry.storage);
    });
    const sheet = labels.length > 0
      ? '<div class="label-sheet">' + labels.join('') + '</div>'
      : '<p class="label-empty-sheet">No drawer to label for the selected storage(s).</p>';
    const doc = '<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">'
      + '<title>Drawer labels</title><style>' + labelSheetCss() + '</style></head><body>'
      + '<div class="label-toolbar"><span>' + BrickTools.fmt.number(labels.length)
      + ' label(s) · A4</span>'
      + '<button type="button" id="label-print">Print</button>'
      + '<button type="button" id="label-close" class="secondary">Close</button></div>'
      + sheet
      + '</body></html>';

    win.document.open();
    win.document.write(doc);
    win.document.close();
    const printButton = win.document.getElementById('label-print');
    if (printButton) {
      printButton.onclick = function () {
        win.focus();
        win.print();
      };
    }
    const closeButton = win.document.getElementById('label-close');
    if (closeButton) {
      closeButton.onclick = function () {
        win.close();
      };
    }
    win.focus();
  }

  /**
   * Opens a printable sheet with one label per drawer of the selected storages.
   * @param {Object} context storages, drawerCards, drawersLoaded, loadDrawers, storageSelection and report
   */
  function exportLabels(context) {
    const ids = context.storageSelection ? context.storageSelection.getSelectedIds() : [];
    if (ids.length === 0) {
      context.report('Select at least one storage to export its labels.');
      return;
    }
    // Open the window before the request so pop-up blockers accept it.
    const win = window.open('', '_blank');
    if (!win) {
      context.report('The label sheet could not be opened; allow pop-ups for this page.');
      return;
    }
    win.document.write('<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">'
      + '<title>Drawer labels</title></head><body><p>Preparing labels…</p></body></html>');
    win.document.close();
    const render = function () {
      writeLabelSheet(win, ids, context.storages, context.drawerCards);
    };
    if (context.drawersLoaded) {
      render();
    } else {
      context.loadDrawers(render);
    }
  }

  BrickTools.PartsCollectionLabels = {
    exportLabels: exportLabels,
    parseLabelSize: parseLabelSize
  };
})();
