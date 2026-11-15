# Generalize the selection control and add it to Part storage

## Summary

Extract the checkbox-selection behavior currently embedded in the Part explorer into a reusable
client-side module, then use that module in the **Part storage** table (`#part-storage`).

The first integration deliberately reuses the generic selection core only: row checkboxes, the
header "select all visible" checkbox, the selected count, **Select all visible**, and
**Clear selection**. Storage-specific bulk actions are intentionally left to a follow-up because
there is currently no bulk/delete endpoint for part storage. The module will expose an action hook
so a future action can be added without changing the selection logic again.

## Findings from the current code

The original draft assumed the selection logic lived in `partscollection-shared.js` and might also
affect `partscollection-pab.js`. That is not the case.

- The real implementation is in
  `src/main/resources/static/js/modules/partscollection-explorer.js`.
  - `selectedPartNumbers` (a `Set`) holds the selection.
  - `updateSelectionUi()` updates the toolbar, the header checkbox, row checkboxes and row styling.
  - `clearSelection()` and `selectVisibleParts()` implement the toolbar actions.
  - `init()` binds the row checkbox, header checkbox, select-all and clear-selection handlers.
  - `renderMainParts()` marks rows with `data-part-number` and the `selected` class, then calls
    `updateSelectionUi()`.
  - Bulk stars and "Add to container" are explorer-specific actions, not generic selection logic.
- `partscollection-shared.js` only contains sorting, reporting, color and order-cell helpers. It does
  not need to know about selection.
- The Part storage table is rendered by `renderStorages()` in
  `src/main/resources/static/js/modules/partscollection-storage.js`; its event handlers are in
  `init()` in the same file.
- The Part storage markup is in `src/main/resources/static/partscollection.html`, in the
  `data-panel="storage"` section.
- The current CSS already has a generic `.selection-toolbar` block, but the row and checkbox styles
  are specific to `#main-parts` (`app.css`, around lines 419-425).
- `renderStorages()` already gives the active container row the `selected` class. A generic
  selection component must not reuse that class, otherwise an active storage would look selected
  even when it is not in the selection.
- The Part storage empty state uses `colspan="8"`. Adding a selection column requires updating it
  to `9`.
- The backend exposes `GET`, `POST` and `PUT /api/part-storage` only. There is no delete or bulk
  update endpoint, so this change should not invent a destructive storage action.

## Scope

In scope:

- a reusable `BrickTools.SelectionControl` module;
- refactoring the Part explorer to use it without changing existing behavior;
- adding the selection column, toolbar and selection state to the Part storage table;
- generic CSS for selectable tables;
- documentation and browser validation.

Out of scope:

- bulk delete, bulk edit, bulk reordering or other new storage operations;
- changing the active-container behavior;
- sharing a selection between tables or tabs.

## Phase 1 - Create the generic selection module

### 1. Add `selection-control.js`

Create:

```text
src/main/resources/static/js/modules/selection-control.js
```

Follow the existing module style: an IIFE with `'use strict'`, jQuery, and a public factory on the
`BrickTools` namespace:

```js
BrickTools.SelectionControl.create(options)
```

Recommended options:

| Option | Purpose |
| ------ | ------- |
| `table` | Table selector or jQuery object whose rows can be selected |
| `toolbar` | Toolbar container; hidden when there is nothing visible and nothing selected |
| `rowSelector` | Row selector inside the table, default `tbody tr` |
| `rowIdAttr` | Data attribute that identifies a row and its checkbox, e.g. `data-part-number` |
| `checkboxSelector` | Row checkbox selector, e.g. `.part-select` |
| `headerCheckbox` | Header "select all visible" checkbox selector |
| `countLabel` | Element that displays the selected count |
| `selectAllButton` | "Select all visible" button selector |
| `clearButton` | "Clear selection" button selector |
| `itemLabel` | Singular noun used in the count, default `item` |
| `onChange` | Optional callback receiving the selected ids and selection info |

The module owns only generic state and UI. It must not know about stars, part numbers, containers,
storages, or any URL.

### 2. Public methods and behavior

Expose a small API:

- `setVisibleIds(ids)` - called by the owning module after every render; accepts the ids currently
  visible after filtering and sorting.
- `getSelectedIds()` - returns a copy of the selected ids as an array.
- `clear()` - clears every selected id.
- `refresh()` - re-applies the current state to the toolbar, header checkbox, rows and checkboxes.

Required behavior:

- Selection is keyed by `String(id)` so row order, sorting and filtering never change its identity.
- `setVisibleIds()` updates the header checkbox and the enabled state of the toolbar controls.
- Selecting or clearing an individual row updates the count and the header checkbox.
- The header checkbox is checked when every visible row is selected, indeterminate when only some
  visible rows are selected, and unchecked otherwise.
- **Select all visible** adds every visible id; it does not remove selections that are currently
  hidden by a filter.
- Unchecking the header checkbox removes only the visible ids, not hidden selections.
- **Clear selection** removes every id, visible or not.
- The toolbar is hidden when `visibleIds.length === 0 && selectedIds.size === 0`; otherwise it stays
  visible so a selection that is temporarily filtered out can still be cleared.
- The count uses the singular/plural label (`1 part selected`, `2 parts selected`) and is driven by
  `itemLabel`.
- The clear button is disabled when nothing is selected; select-all and the header checkbox are
  disabled when nothing is visible.
- The selected row class is `.row-selected`, not `.selected`, to avoid colliding with existing
  "active container" styling.
- `onChange(ids, info)` runs after user and programmatic selection changes so consuming modules can
  enable, disable or configure their own action buttons.

Bind events with a namespace such as `.selectionControl` so repeated initialization cannot leave
duplicate handlers behind. If the same table can be initialized twice, call `destroy()` first or
make initialization idempotent; adding `destroy()` to the API is acceptable if it keeps the module
simple.

### 3. Refactor the Part explorer

In `partscollection-explorer.js`:

1. Remove `selectedPartNumbers`, `clearSelection()`, `selectVisibleParts()`, and
   `updateSelectionUi()`.
2. Replace `selectionCell(part)` with a generic cell that uses a shared class, for example
   `.part-select`, and still carries `data-part-number`.
3. In `init()`, create one controller:

   ```js
   selection = BrickTools.SelectionControl.create({
     table: '#main-parts',
     toolbar: '#main-parts-selection',
     rowIdAttr: 'data-part-number',
     checkboxSelector: '.part-select',
     headerCheckbox: '#main-parts-select-all-visible',
     countLabel: '#main-parts-selected-count',
     selectAllButton: '#main-parts-select-all',
     clearButton: '#main-parts-clear-selection',
     itemLabel: 'part',
     onChange: updateExplorerBulkActions
   });
   ```

4. `updateExplorerBulkActions(ids)` disables or enables the explorer-only controls:
   `#main-parts-bulk-stars .star` and `#main-parts-bulk-add`.
5. In `renderMainParts()`, replace the four `updateSelectionUi()` calls with
   `selection.setVisibleIds(...)`:
   - empty Search result: `selection.setVisibleIds([])`;
   - no visible rows: `selection.setVisibleIds([])` while keeping existing selections;
   - normal render: `selection.setVisibleIds(visible.map(part => String(part.partNumber)))`.
6. When a different category or search is selected, call `selection.clear()` exactly where
   `clearSelection()` was called before.
7. Keep `saveSelectedMainPartStars()` and `addPartsToContainer()` in the explorer. Retrieve ids with
   `selection.getSelectedIds()` and clear with `selection.clear()`.
8. Delete the old checkbox, header-checkbox, select-all and clear-selection handlers from `init()`;
   the module owns them now.
9. Keep the existing star buttons, add-to-container, alternates and search handlers unchanged.

### 4. Load the new script

In `partscollection.html`, add the module after `common.js` and before the modules that use it:

```html
<script src="js/modules/selection-control.js"></script>
<script src="js/modules/partscollection-shared.js"></script>
<script src="js/modules/partscollection-storage.js"></script>
...
```

`partscollection-pab.js` does not need to change.

## Phase 2 - Add selection to `#part-storage`

### 1. Add the markup

In the `data-panel="storage"` section of `partscollection.html`:

- Add a selection toolbar between `.storage-toolbar` and `.table-wrap`:

  ```html
  <div id="part-storage-selection" class="selection-toolbar" hidden>
    <span id="part-storage-selected-count" class="muted">0 storages selected</span>
    <button type="button" id="part-storage-select-all" class="btn secondary"
            data-icon="selectAll">Select all visible</button>
    <button type="button" id="part-storage-clear-selection" class="btn secondary"
            data-icon="clear">Clear selection</button>
  </div>
  ```

- Add a selection column as the first column of the `#part-storage` header:

  ```html
  <th class="select-col">
    <input type="checkbox" id="part-storage-select-all-visible"
           aria-label="Select all visible storages" title="Select all visible storages">
  </th>
  ```

- Update the empty-row `colspan` from `8` to `9`.

Use `storages` as the count label (`itemLabel: 'storage'`).

### 2. Instantiate the controller

In `partscollection-storage.js`:

1. Add a module-level `let storageSelection = null;`.
2. In `init()`, create the controller with the storage selectors and
   `itemLabel: 'storage'`.
3. In `renderStorages()`:
   - Add `data-storage-id` to every generated row.
   - Prepend a selection cell with a `.storage-select` checkbox carrying `data-storage-id`.
   - Call `storageSelection.setVisibleIds([])` in the no-results branch.
   - Call `storageSelection.setVisibleIds(visible.map(storage => String(storage.id)))` after the
     rows are rendered.
4. Nothing else should be needed for the generic controls.

Do not add bulk actions yet. The module's toolbar contains only the generic controls. If a storage
action is desired before a follow-up, the only safe existing operation is **Set active**, and it
should require exactly one selected row. Do not invent a bulk delete UI without a backend endpoint
and a rule about what happens to the storage's part collection rows.

### 3. Fix the row-state class collision

`renderStorages()` currently uses `toggleClass('selected', isActive)`. Change that to a distinct
class, `active-storage-row`, so the generic `.row-selected` class can be used for checkbox selection
without making the active storage look selected. Avoid the name `active-container`: it is already
used by the application header, including a responsive `.active-container` rule that would leak
into table rows.

Update the corresponding CSS. This also makes the meaning of both states explicit:

- `.row-selected`: the row is checked in the selection;
- `.active-storage-row`: the row is the active container shown in the header.

### 4. Generalize the CSS

In `src/main/resources/static/css/app.css`:

- Keep the existing `.selection-toolbar` and `.selection-stars` rules.
- Replace `.main-part-select-col` with a generic `.select-col` and apply it to both tables' header
  and body cells. Keep the old class as an alias only if another page still uses it.
- Replace `#main-parts tbody tr.selected` and its hover rule with a generic rule such as
  `table.data tbody tr.row-selected`.
- Add an `active-storage-row` rule that is visually distinct from `row-selected` (for example a
  left border or a slightly different background).
- Keep the existing responsive `.selection-toolbar` rules; verify that the storage toolbar wraps
  correctly at narrow widths.

Prefer generic selectors over ids so future tables can use the same component.

### 5. Update the documentation

After implementation, update `doc/PartsCollectionPage.md`:

- Describe the reusable selection behavior in the Part explorer, replacing implementation-specific
  wording if needed.
- Add the selection column and generic selection toolbar to the **Part Storage Tab** section.
- State explicitly that storage bulk actions are not provided until a backend operation exists.

`doc/CommonFeatures.md` only needs an update if the selection control is added to another page
later.

## Validation

### Part explorer regression

- Selecting one row updates the count, the row style and the action buttons.
- The header checkbox is checked, indeterminate and unchecked for all/some/none of the visible rows.
- **Select all visible** adds only filtered rows; hidden selections survive.
- **Clear selection** removes hidden selections too.
- Selection survives sorting and filtering.
- Changing category or running a different search clears the selection.
- Bulk stars still updates every selected part, then re-renders.
- Bulk **Add to container** still adds every selected part and clears the selection on success.
- The toolbar is hidden when there are no rows and nothing selected, and visible when a selection is
  filtered out.

### Part storage

- Each row has a checkbox and the header checkbox selects/clears visible storages.
- The count shows `1 storage selected` and `N storages selected` correctly.
- Filtering and sorting preserve selections; select-all affects only visible storages.
- The toolbar is hidden when the list is empty, and the empty row spans all nine columns.
- The active storage row remains visually distinct from selected rows.
- View, Edit and Set active row actions still work.
- The table remains usable at mobile widths and the toolbar wraps without overlapping the table.

### General checks

- No JavaScript errors in the browser console.
- No duplicate checkbox/clear/select-all handlers after switching tabs or reloading data.
- `mvn test` still passes; this change is frontend-only unless a storage bulk action is added later.

## Risks and mitigations

| Risk | Mitigation |
| ---- | ---------- |
| Selection state is lost or stale after a re-render | Keep ids as strings, call `setVisibleIds()` on every render, and prune ids that no longer exist if a reload can remove rows |
| Duplicate event handlers after repeated init | Namespace events and create the controller once per table |
| Active storage and selected storage look identical | Use separate `active-storage-row` and `row-selected` classes |
| Select-all accidentally clears hidden selections | Never rebuild the selected set from visible rows; only add or remove visible ids |
| Toolbar disappears while a selection is filtered out | Hide it only when both visible and selected counts are zero |
| Empty state layout breaks after adding a column | Update `colspan` to `9` and verify with no data and with no filter matches |
| A future storage bulk action is added without an API | Add a `DELETE`/bulk endpoint and define rule handling first; the selection module's action hook is ready for it |

## Definition of done

- `selection-control.js` exists and contains no explorer- or storage-specific logic.
- `partscollection-explorer.js` uses the shared controller and its behavior is unchanged.
- `#part-storage` has a checkbox column and a working generic selection toolbar.
- CSS uses generic selectable-table classes and active/selected states are distinct.
- The new script is loaded in `partscollection.html`.
- `doc/PartsCollectionPage.md` describes the storage selection behavior.
- The manual validation checklist above passes.

## Open decisions for a follow-up

1. **Storage bulk actions.** Recommended follow-up: bulk delete selected storages. That requires a
   backend endpoint and a decision on existing `PartCollection` rows (reject deletion when rules
   exist, or delete the rules with the storage).
2. **Selection after storage reload.** Recommended: preserve ids that still exist and silently drop
   ids that disappeared.
3. **Selection scope.** Keep one selection per table; do not share selection between the explorer
   and the storage table.
