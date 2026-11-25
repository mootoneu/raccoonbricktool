# Project Review: Raccoon Brick Tools

Scope: full read-only review of the backend (`src/main/java`), the frontend (`src/main/resources/static`), the tests, the configuration and the documentation. No code was modified. Line numbers refer to the state of the repository at review time.

## 1. Summary

The project is functional and reasonably organised at the top level: one Spring Boot backend split by domain packages, CSV storage behind a small shared helper (`Csv`), a documented API, and a documented data model. The last recorded Maven run (surefire reports of 24 November 2026) is green, with live tests skipped. The code is consistent in its conventions (Javadoc on services, `Optional` for lookups, path-traversal guards on image endpoints).

The main risks are concentrated in four areas:

1. **A secret ships inside the build artifact.** The BrickOwl API key is packaged into the jar.
2. **Concurrency design.** Almost every service method is `synchronized` on a per-instance lock, including methods that perform network I/O and `Thread.sleep`. Shared CSV files are written from several services, so the per-instance locks do not protect them.
3. **Size and duplication.** Several files are far too large (a 1,957-line JavaScript module, a 1,022-line controller, a 975-line service), and whole helper blocks are copied between modules.
4. **UI redundancy.** The Parts Collection page shows the same data in up to three places, and the modal and tab markup diverges between pages.

Overall rating: **solid for a personal single-user tool; needs a focused refactoring pass before it grows further.**

| Area | Rating | Short justification |
| --- | --- | --- |
| Code quality, readability, maintainability | Medium | Clear naming and Javadoc on services, but oversized classes and repeated helpers. |
| Implementation coherence | Medium-low | Inconsistent API naming, file naming, sorting rules and version strings. |
| Coding guidelines and standards | Medium | Consistent style within a file; no linter, formatter or JS tests; no shared conventions doc. |
| UI quality and redundancy | Medium-low | Good visual language and icon system, but redundant views, divergent modals, weak keyboard and ARIA support. |
| File size and modularity | Low | Several god-files; backend packages are well separated but the controller and storage service are not. |
| Security | Medium | Secret in artifact, server bound to all interfaces, no authentication, `@CrossOrigin("*")`. |

## 2. Metrics

| Metric | Value |
| --- | --- |
| Java main sources | 40 files, largest `ApiController.java` (1,022 lines), `PartStorageService.java` (975 lines), `BrickOwlService.java` (488 lines) |
| Java test sources | 30 files (one is a support class), all green in the last recorded run; live tests are skipped |
| JavaScript | 26 files; largest `partscollection-storage.js` (1,957 lines), `partscollection-explorer.js` (805), `common.js` (587) |
| CSS | `app.css`, 797 lines |
| HTML pages | 6 pages, `partscollection.html` at 432 lines |
| Controller endpoints | 65 mappings in one class with 22 constructor dependencies |
| `synchronized` occurrences | 72 across the services |
| Duplicated helpers | `clean` and `toInt` redefined in 11 Java classes; sort, colour and report helpers duplicated across two JS modules |

## 3. Findings by requested area

### 3.1 Code quality, readability and maintainability

**Strengths**

- Service classes have Javadoc that explains the behaviour, not just the signature (`BackupService`, `SetInventoryService`, `PartStorageService`).
- Input validation is explicit and produces readable messages (`PartStorageService.validateDrawer`, `normalizeLabelSize`).
- Image endpoints validate names with a regex and check the normalised path against the folder, which prevents path traversal (`OtherImageService.find`, `SetImageService.find`).
- `SetInventoryService` streams the 131 MB `inventory_parts.csv` and caches results against a file stamp. This is the right pattern for large reference files.
- The frontend has a clear shared layer (`common.js`) with an icon system, API wrapper, header, tabs and active-container handling.

**Issues**

- **M1. God controller.** `ApiController` (1,022 lines) exposes 65 endpoints for about 15 domains and takes 22 constructor dependencies. Its `@Operation` descriptions are long and repeat file paths. Business logic leaks in: `partColors()` (lines 857–876) joins colour data itself and belongs in `PartImageService` or `ColorService`.
- **M2. Repeated image-serving code.** `setImage`, `themeImage`, `vendorImage` and `peopleImage` each repeat the same six-line `Optional` → `ResponseEntity` block. A private `otherImage` helper already exists but only `peopleImage` uses it. `partImage` and `partImageByItemNumber` repeat it again.
- **M3. Overloaded names for different endpoints.** `updateMainPartStars` is used for both `PUT /parts/main-parts/{partNumber}/stars` (line 776) and the bulk `PUT /parts/main-parts/stars` (line 789). `pabPartPrices` is the name of two endpoints. Names should describe the operation (`updateMainPartStar`, `updateMainPartStarsBulk`).
- **M4. God service.** `PartStorageService` (975 lines) mixes four responsibilities: storage CRUD, collection-rule CRUD, drawer expansion and search, and catalogue loading. It also builds UI view models (`partChips`, `categoryChips`, `colorChips`, lines 551–629), which is presentation logic in the domain layer.
- **M5. Duplicated Java helpers.** `clean` and `toInt` are copied into 11 classes (`PeopleService`, `ColorService`, `NewSetService`, `PabService`, `SetInventoryService`, `WishlistService`, `PurchaseService`, `PartImageService`, `PabPartPriceService`, `PartService`, `PartStorageService`). Some variants differ: `PartImageService.toInt` returns `int`, others return `Integer`.
- **M6. Untyped data flow.** Almost every service returns `Map<String, Object>` or `List<Map<String, String>>`. There are no DTO records for responses. Keys are strings that must match between Java and JavaScript, and nothing checks them at compile time.
- **M7. Repeated read-modify-write pattern.** The pattern "read the CSV, find a row, change it, write the whole file" appears in many service methods. `BrickWatchService.price` and `BrickonomicsService.fetchPrice` contain near-identical code.
- **M8. Repeated CSV reads in one request.** `PurchaseService` reads `purchasesFile` three times in separate methods, `PartService` reads `mainPartsFile` on every call without caching, and `PartStorageService.drawers` rescans the whole collection per drawer.
- **M9. Frontend global-namespace coupling.** Modules attach themselves to `window.BrickTools` and depend on load order in the HTML (`common.js`, then `color.js`, then the modules). `BrickColor` is used as a global without being declared as a dependency.
- **M10. Spelling in persisted names.** `ExpectedQut` and `ActualQut` (`SetInventoryService` line 31, and the JSON keys `expectedQut` and `actualQut`) mean "quantity". Renaming requires a CSV migration, so it should be planned, not patched.

### 3.2 Implementation coherence

- **C1. Inconsistent sorting of storages.** `PartStorageService.storages()` sorts by `Order` then `Id` (lines 73–75). `drawers()` sorts storages by `Id` only (line 179). The storage table and the drawer selector can therefore show a different order. Verify in the UI and align both on `Order`, then `Id`.
- **C2. Silent no-op in `addParts`.** In `PartStorageService.addParts` (lines 249–252), if an existing row has a blank `Amount`, the part is counted as added but the amount is not changed and nothing is reported. Either treat blank as 1 or reject the row.
- **C3. Duplicate colour sources.** Colour data is read independently in `ColorService`, `PartStorageService.catalog()`, `PartImageService`, `SetInventoryService` and `PabPartPriceService`. Each has its own cache and its own lookup rules. Consider a single `ColorCatalog` bean.
- **C4. Version strings disagree.** `pom.xml` is `1.0.0`, `OpenApiConfig` says `0.1.0` (line 15), and the BrickWatch user agent says `BrickTools/0.1` (`BrickWatchService` line 23).
- **C5. Brand names disagree.** The header shows "RaccoonBrick Tools" (`common.js` line 377, no space), the page titles say "Raccoon Brick Tools", the README says "Brick Tools" and the Maven name is "Brick Tools".
- **C6. Inconsistent API naming.** Examples: `/api/setimg/...` next to `/api/sets/{setNumber}/inventory`; `/api/part-storage` and `/api/part-collection` (kebab-case) next to `/api/parts/...` and `/api/pab/...`; `/api/le/pab-part-prices` (the `le` segment means "LEGO data", which is an internal folder name, not a resource). Establish one convention and document it.
- **C7. Inconsistent `produces` and response handling.** Some mappings declare `produces`, others do not. Some return `ResponseEntity`, others use `@ResponseStatus`. `@CrossOrigin(origins = "*")` appears on two endpoints only.
- **C8. Inconsistent data file naming and placement.** `bt/SetInventory.csv` is PascalCase while every other file is snake_case or lower-case. `ThemeImageService` stores images under `data/le/themes`, although the README defines `le/` as LEGO price data and `bt/` for Brick Tools data. The README data table should be aligned with the code, or the code with the README.
- **C9. Error semantics.** `RebrickableService.update` throws `IllegalStateException` for "already downloaded today", which the handler maps to HTTP 400. A 409 would describe the conflict better. `ApiExceptionHandler` returns raw `IOException` messages, which can expose local file paths.
- **C10. Documentation is largely accurate.** The endpoint tables in `doc/*.md` match the controller. Only minor drift was found (documented behaviour, such as the 500-result cap on main-part search, matches the code). Keep this in sync when the naming in C6 changes.

### 3.3 Coding guidelines and standards

- **G1. No enforced standard.** There is no formatter, linter or EditorConfig. Java indentation is two spaces, which is consistent, but the frontend mixes `function` declarations, arrow functions, and `const`/`let` styles, and quotes are mixed in places. Add `.editorconfig`, a Checkstyle or Spotless configuration for Java, and ESLint or Prettier for JavaScript.
- **G2. No automated frontend tests.** The only check is `node --check`, as documented in the README. Logic such as `compareValues`, `parseSizeQuery`, `getPartNameFromBOName` and `readableText` is pure and easy to unit-test with Jest or Node's built-in test runner.
- **G3. Synchronisation and I/O.** `synchronized` is used on nearly every service method as a blanket safety measure. See section 4 (A1) for the consequences.
- **G4. Logging.** SLF4J is used in a few places only. Most failures are returned to the client and not logged. Log every unexpected exception at error level in `ApiExceptionHandler` (only the client-facing message is returned).
- **G5. Hard-coded values.** The BrickOwl user ID `934717` is hard-coded in `partscollection-shared.js` (line 8) and `brickowl-orders.js` (line 45). Move it to configuration or derive it from the API response. Likewise, the sleep constants and cron expression are fine as constants, but the BrickWatch user agent should use the project version.
- **G6. Comments.** The README asks for comments on complex logic, and this is mostly respected in services. Some JavaScript functions carry JSDoc (`brickowl-shared.js`) while others carry none. Pick one level and apply it to the whole frontend.
- **G7. Dead or unused resources.** `d3` is loaded on `partscollection.html` but only the BrickOwl and Sets Purchase statistics modules use it (see U4). `scripts/migrate-main-parts-size.mjs` is a one-off migration that could move to a `scripts/archive/` folder once it has run.
- **G8. Build hygiene.** `target/` is ignored, but `target/classes` and surefire dumps are present in the working tree. `desktop.ini` is untracked. Neither is a problem for the repo, but both are noise.

### 3.4 UI quality and redundancy

**Strengths**

- A coherent visual language: the LEGO-inspired palette is defined as CSS variables, the header and tabs are consistent, icon-only buttons have `title` and `aria-label`, and status chips are used consistently.
- Several modals can be closed with Escape, and the modal code has keyboard handling in more than one module.
- Tab state and the last visited page are remembered in `localStorage`, which is useful for a daily-use tool.
- Images fall back through a chain of sources (`partImageCell`), which avoids broken images.

**Redundancy**

- **U1. The Parts Collection page shows the same data several times.**
  - Drawer cards appear in the **Collection** tab (`#collection-drawers`, `partscollection.html` line 91) and again in the **Storage drawers** tab (`#drawer-cards`, line 183), with near-identical filters and rendering.
  - BrickOwl parts appear in the **Collection** search results (`#collection-brickowl-parts`, line 95) and in the **BrickOwl** tab (`#bo-parts-collection`, line 242). The two tables have different sorting: the search results cannot be sorted, the tab can.
  - PaB parts appear in the **Collection** search results (`#collection-pab-parts`, line 116) and in the **PaB Parts** tab (`#pab-parts`, line 222), with different column sets (for example, the category column is first in one and absent in the other).
  - Recommendation: keep one table component per data source, with one sort implementation, and reuse it in both places. Or decide that the search results are the only place for cross-source views and remove the duplicated tabs.
- **U2. Tabs are a navigation pattern with overlapping purpose.** Seven tabs for one page: "Part explorer", "Collection", "Part storage", "Storage drawers", "PaB Purchases", "PaB Parts", "BrickOwl". "Storage drawers" and "Collection" overlap, and "Part storage" and "Storage drawers" describe the same entities. Consider grouping into "Explore", "Find", "Storage" and "Sources".
- **U3. Modal markup diverges.**
  - Four static form modals (`#pab-purchase-modal`, `#pab-part-modal`, `#storage-modal`, `#collection-rule-modal`) use a bare `<form>` with no `role="dialog"`, no `aria-modal`, and no `aria-labelledby`.
  - `#main-part-alternates-modal` uses a `.modal-dialog` wrapper with `role="dialog"`. It is the only modal written this way.
  - Several modals are built in JavaScript at runtime (`sets.js` `ensureSiteModal`, `ensurePriceModal`; `color.js` `ensureColorInfoModal`, `ensureColorPickerModal`; `part-info.js` `ensureModal`). Each has its own open, close and Escape logic.
  - Recommendation: one modal helper in `common.js` (open, close, focus trap, Escape, `aria-labelledby`) used by static and dynamic modals.
- **U4. Unneeded third-party script.** `partscollection.html` loads d3 (line 420) but no module on that page uses it.
- **U5. Duplicated colour chip rendering.** Colour chips are built by `renderColorCell` in `brickowl-shared.js` and `partscollection-shared.js` (with different click behaviour), by `colorChip` in `color.js`, and by `pabColorChip` in `partscollection-pab.js`. `readableText` is defined in `common.js`, `part-info.js` and `newsets-shared.js`.

**Quality and consistency issues**

- **U6. Inconsistent naming and casing.** Page heading "Parts collection" (sentence case), tabs "Part explorer", "PaB Purchases", "PaB Parts" (mixed), buttons "Export Labels" (title case) next to "Add storage" (sentence case). Choose sentence case throughout, and use "PaB" consistently.
- **U7. Branding.** "RaccoonBrick Tools" in the header, "Raccoon Brick Tools" in titles, "Brick Tools" in the README. Choose one display name.
- **U8. Accessibility gaps.**
  - Sortable column headers (`th[data-key]`, for example `partscollection.html` lines 64–70) are click-only. They have no `tabindex`, no keyboard handler and no `aria-sort`.
  - The tab bar (`<nav class="tabs">`, lines 16–24) has no `role="tablist"`, `role="tab"`, `aria-selected` or `aria-controls`.
  - Colour chips are clickable to open colour details. The chips built in `partscollection-shared.js` and `brickowl-shared.js` handle Enter and Space, but `color.js` `colorChip` (used elsewhere) does not set `tabindex` or keyboard handling, so behaviour depends on which page built the chip.
  - Part images have `role="button"` only when a part number is available, and the image `alt` is the label, which is good; but the same name appears in `title`, which doubles announcements.
- **U9. Dependence on external CDNs.** jQuery and d3 are loaded from `code.jquery.com` and `cdn.jsdelivr.net` without `integrity` attributes. The app is self-hosted and described as usable offline, so it breaks without internet access. Vendor the libraries into `static/vendor/` (or add SRI hashes at minimum).
- **U10. Repeated styling values.** `app.css` (797 lines) defines colour variables in `:root` but still hard-codes about 80 colours, for example `#fef9c3`, `#dcfce7`, `#eaf4fc`. Promote them to variables (status, row highlight, priority, selection) so theming is possible.
- **U11. Hard-coded external user ID in links.** Covered in G5. The link text and target should come from configuration.

### 3.5 File size and modularity

| File | Lines | Assessment |
| --- | --- | --- |
| `static/js/modules/partscollection-storage.js` | 1,957 | Too large. It mixes storage CRUD, drawers, collection search, the rule editor, label export and category loading. `init()` alone wires about 350 lines of events. |
| `web/ApiController.java` | 1,022 | Too large. One controller for 15 domains. |
| `storage/PartStorageService.java` | 975 | Too large. Four responsibilities. |
| `static/js/modules/partscollection-explorer.js` | 805 | Large but cohesive. Could split tree rendering from main-part table and alternates modal. |
| `static/css/app.css` | 797 | Single file. Split by component or page (`base`, `layout`, `components`, `pages/*`). |
| `static/js/common.js` | 587 | Mixes icons, API client, header, tabs, storage, active container and images. Split into `icons.js`, `api.js`, `header.js`, `tabs.js`, `active-container.js`, `images.js`. |
| `static/js/modules/partscollection-pab.js` | 502 | Acceptable, but it duplicates colour and table logic. |
| `static/js/modules/purchases-inventory.js` | 490 | Acceptable. |
| `static/js/sets.js` | 484 | Mixes page logic with modal builders. Move modal builders to a shared modal module. |
| `wishlist/NewSetService.java` / `purchase/SetInventoryService.java` | 225 / 412 | Acceptable. |

Backend packages are well separated by domain (`brickowl`, `pab`, `storage`, `wishlist`, `setimg`, ...). The problem is within the largest classes, not between packages.

Recommended split for the backend:

- `ApiController` → one controller per domain: `PartController`, `PartStorageController`, `PabController`, `BrickOwlController`, `PurchaseController`, `WishlistController`, `AdminController`, `ImageController`, `PriceController`.
- `PartStorageService` → `StorageService` (storages), `CollectionRuleService` (rules), `DrawerService` (expansion and search), `PartCatalog` (shared catalogue loading). Move the chip view-model building to the controller or to a `DrawerView` mapper.
- `Csv` → keep, and add a `CsvTable` helper (load, find, update, save) to replace the repeated read-modify-write blocks.

## 4. Security and correctness findings

These were not requested explicitly but are important.

| ID | Severity | Finding | Location | Proposal |
| --- | --- | --- | --- | --- |
| A1 | High | **Secret packaged in the artifact.** `src/main/resources/.env` contains `BRICKOWL_API_KEY`. It is git-ignored, but it is copied into `target/classes` and into the jar (`BOOT-INF/classes/.env`). Nothing in the code reads it. | `src/main/resources/.env`, `target/bricktools-1.0.0.jar` | Delete the file from `src/main/resources` (move it to the project root, outside the classpath, if a local file is needed). Rotate the key if the jar has ever been shared. Add an explicit check in the build (for example, a Maven enforcer or a test that fails if `.env` is under `resources`). |
| A2 | High | **Server listens on all interfaces without authentication.** `application.yml` sets no `server.address`, so Spring binds to every interface. The API can trigger BrickOwl updates, backups, Rebrickable downloads and file writes. Anyone on the local network can use it. | `application.yml` | Add `server.address: 127.0.0.1` (or make it configurable). If LAN access is wanted, add authentication. |
| A3 | Medium | **Blanket `@CrossOrigin("*")`** on two endpoints (`/le/pab-part-prices`, lines 699 and 709). Any website visited in the same browser can read these prices. | `ApiController` | Remove the annotation (the frontend is served from the same origin) or restrict it to known origins. |
| A4 | Medium | **Internal exception messages returned to clients.** `ApiExceptionHandler.ioFailure` returns `e.getMessage()`, which can contain local paths. | `ApiExceptionHandler` | Return a generic message, log the exception with its stack trace, and keep the detailed message server-side. |
| A5 | Medium | **Blocking I/O inside `synchronized` methods.** Slow network calls and sleeps hold the service lock, which stalls unrelated requests. | Several services | See section 5 (P2). |
| A6 | Low | **Cross-service CSV writes are not coordinated.** `bw/setprices.csv` is written by `BrickWatchService` and `BrickonomicsService`, each with its own lock, so concurrent updates can drop rows. `Csv.write` uses a fixed `file.tmp` name, so two writers to the same file can overwrite each other's temporary file. | `BrickWatchService`, `BrickonomicsService`, `Csv.java` line 38 | Share one lock per file (a `FileLocks` registry), or route all writes for a file through one service. Use a unique temporary name (`Files.createTempFile` in the same directory). |
| A7 | Low | **BrickWatch price parsing.** `parsePrice` strips everything except digits, `,` and `.`, then replaces `,` with `.`. A value such as `1.299,99` becomes `1.299.99` and returns `null`, which is then reported as "No official price found". | `BrickWatchService.parsePrice` (lines 110–120) | Handle `.` as a thousands separator when both separators are present. Add a unit test. |
| A8 | Low | **Silent truncation of search results.** Main-part search returns at most 500 results and drawer matches at most 200 (`MATCH_LIMIT`, `DRAWER_MATCH_LIMIT`). This is documented for part search, but the drawer cap is silent in the UI. | `PartStorageService`, `PartService` | Return a `truncated` flag and show it in the UI. |

## 5. Concurrency and performance review

- **P1. Blanket `synchronized` on service methods.** More than 60 methods are `synchronized` on the service instance. This serialises all requests to a service, including reads of cached data. It is safe, but it is the wrong granularity.
- **P2. Network and sleep inside locks.** These methods hold the instance lock during slow operations:
  - `RebrickableService.update()` downloads the full archive (tens of MB) and extracts it while holding the lock. `status()`, which the Administration page polls, is also `synchronized`, so it blocks until the download finishes.
  - `BrickWatchService.price()` sleeps for the throttle delay and performs the HTTP request inside the lock, so every price lookup, including cached ones, waits.
  - `SetImageService.find()` is `synchronized` and is called on every image request. `ensure()` downloads while holding the lock, so image requests stall during a download. `find()` also lists the whole image directory on every call.
  - `PartImageService` downloads images from the CDN in synchronized methods.
  - Proposal: separate reads from writes with a `ReentrantReadWriteLock` per file, or use a single-writer executor for downloads. Never hold a lock across `HttpClient.send` or `Thread.sleep`.
- **P3. Non-atomic writes in `RebrickableService.extract`.** Each CSV is copied straight into `data/re/`. A failure part-way through leaves a mix of old and new files. Extract to a temporary directory and move each file atomically once the archive is complete.
- **P4. Repeated reads.** `PurchaseService` reads `purchasesFile` three times across methods. `PartService` reads `mainPartsFile` on every request. `PartStorageService.drawers()` rescans the collection for each drawer, and `explicitlyListed` rescans all rows for every candidate part (lines 505–518, called from `colorsMatch`). This is O(drawers × rows × parts) on the "Other" colour keyword. Build an index once per request or cache the parsed file against its stamp, as `SetInventoryService` already does.
- **P5. Scheduled job.** `RebrickableService.checkStaleness` runs on `ApplicationReadyEvent` and daily at 09:00. Both paths are `synchronized`. This is fine, but it should not share a lock with `update()` (see P2).

## 6. Test coverage

- Java: 29 test classes (plus one support class) covering services, the REST API and utilities. The last recorded run has no failures. Live tests are correctly skipped by default.
- Gaps:
  - No test for concurrent CSV writes (A6).
  - No test for the BrickWatch thousands-separator case (A7).
  - No test for `addParts` with a blank `Amount` (C2).
  - No test for the drawer sorting consistency (C1).
  - No frontend tests at all (G2).
- The test directory layout mirrors the main package, which is good.

## 7. Actionable plan

Priorities: **P0** = do now (security); **P1** = correctness and concurrency; **P2** = maintainability refactor; **P3** = UI polish. Effort: S (under half a day), M (1–3 days), L (more than 3 days).

### P0: security (do now)

1. Remove `src/main/resources/.env` from the classpath, rotate the BrickOwl key if the jar was shared, and verify with `jar tf` that the artifact is clean. *(A1, S)*
2. Set `server.address` to `127.0.0.1` in `application.yml`, or document the LAN risk. *(A2, S)*
3. Remove `@CrossOrigin("*")` from the price endpoints. *(A3, S)*
4. Return generic messages from `ApiExceptionHandler` and log the detail. *(A4, S)*

### P1: correctness and concurrency

5. Move slow I/O out of `synchronized` methods, starting with `RebrickableService.status()`, `BrickWatchService.price()` and `SetImageService.find()`. *(P2, M)*
6. Make Rebrickable extraction atomic (extract to a temp directory, then move). *(P3, S)*
7. Share one lock per CSV file across services, and use unique temp file names in `Csv.write`. *(A6, S)*
8. Fix the blank-`Amount` no-op in `PartStorageService.addParts`. *(C2, S)*
9. Align storage ordering between `storages()` and `drawers()`. *(C1, S)*
10. Fix the BrickWatch thousands-separator parsing and add a test. *(A7, S)*
11. Cache or index the catalogue and collection data used by `drawers()` and `explicitlyListed`. *(P4, M)*

### P2: maintainability refactor

12. Split `ApiController` by domain (section 3.5). *(M1, M)*
13. Move `partColors()` and the chip view-model building out of the controller and the storage domain. *(M1, M4, M)*
14. Replace the four duplicated image-serving methods with one helper. *(M2, S)*
15. Create a shared `CsvTable` (or similar) helper for the read-modify-write pattern, and extract `clean`, `toInt` and `isNumeric` into a `Values` utility. *(M5, M7, S)*
16. Split `PartStorageService` into storage, rule, drawer and catalogue services. *(M4, L)*
17. Introduce response DTO records for the most-used endpoints (purchases, storages, collection rules, parts). *(M6, L)*
18. Remove the duplicated `brickowl-shared.js` / `partscollection-shared.js` helpers and keep one copy in `common.js` or a `shared/` module. Reconcile the two `renderColorCell` implementations. *(section 3.4 U5, S)*
19. Split `partscollection-storage.js` into storage, drawers, collection search, rule editor and labels modules. *(file size, M)*
20. Split `common.js` into focused files and split `app.css` by component. *(file size, S)*
21. Consider native ES modules (`type="module"`) to replace the `window.BrickTools` global pattern without introducing a build step. *(M9, M)*

### P2: standards and tooling

22. Add `.editorconfig`, a Java formatter or Checkstyle configuration, and ESLint or Prettier for the frontend. *(G1, S)*
23. Add unit tests for the pure JavaScript helpers (sorting, search parsing, colour contrast, part name cleanup). *(G2, M)*
24. Log unexpected exceptions consistently with SLF4J. *(G4, S)*
25. Move the BrickOwl user ID to configuration. *(G5, S)*

### P2: API and data naming (plan a migration)

26. Decide one URL convention (kebab-case resources, consistent prefixes) and document it. *(C6, M, breaking)*
27. Rename `ExpectedQut` / `ActualQut` and the matching JSON keys, with a CSV migration script in `scripts/`. *(M10, M, breaking)*
28. Align `bt/SetInventory.csv` with the snake-case convention, and move the theme images to the folder the README describes. *(C8, S, needs migration)*
29. Use one version string, sourced from the Maven project, for `OpenApiConfig` and the BrickWatch user agent. *(C4, S)*
30. Choose one display name for the application and apply it to the header, titles and README. *(C5, U7, S)*

### P3: UI

31. Remove the duplicated tables on the Parts Collection page, or make them share one component (U1, M).
32. Simplify the Parts Collection tabs into fewer, clearer groups (U2, M).
33. Introduce one modal helper and use it for every modal, static and dynamic, with the `role="dialog"`, `aria-modal` and `aria-labelledby` attributes (U3, M).
34. Make sortable headers keyboard-operable with `tabindex="0"`, Enter/Space handling and `aria-sort`, and add `role="tablist"` semantics to the tab bar (U8, S).
35. Vendor jQuery and d3 under `static/vendor/`, or at least add `integrity` attributes, and remove d3 from pages that do not use it (U9, U4, S).
36. Promote the hard-coded colours in `app.css` to variables (U10, S).
37. Use sentence case for all headings, tabs and buttons, and use "PaB" consistently (U6, S).
38. Surface truncated search results in the UI (A8, S).

## 8. Things that are working well (keep them)

- Path-traversal protection on image endpoints (regex plus normalised-path check).
- The `Csv.write` atomic-move approach for file updates (once the temp name is made unique).
- The stamp-based caches in `SetInventoryService` and `PartStorageService.catalog`.
- The consistent icon system with `title` and `aria-label` on icon-only buttons.
- Documentation that tracks the API closely, and a README that explains the data model and configuration.
- Clear validation messages in the storage and collection services.
- Live tests that are skipped by default, so the normal build does not depend on external services.

## 9. Limitations of this review

- The review is static. The application was not started and the test suite was not re-run; the statements about test results come from the surefire reports already in `target/`.
- The UI was reviewed from its markup and scripts, not by running it in a browser, so visual issues (spacing, responsive layout) are not assessed.
- The `.env` finding was confirmed by listing the jar's entries. Its value was not read or printed.
