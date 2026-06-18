# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

AppSnapshoter is a root-required Android backup/restore app that creates compressed snapshots of app data (APK, data dirs, OBB, media). It uses MVVM with ViewBinding/DataBinding (no Compose), communicates with root services via AIDL + libsu, and uses native JNI for TAR/ZSTD compression.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew :app:installDebug      # Install debug on connected device
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (needs device)
./gradlew :provider:build        # Build a single module
```

Build uses Gradle 9.2.1, AGP 9.0.0, Kotlin 2.3.0, Java 21, compileSdk 36, minSdk 28. NDK 25.2.9519653 with CMake 3.22.1 for native builds.

## Module Architecture

```
app → api, hiddenapi, provider
provider → api, hiddenapi, systemapi, io-nativefs, io-tar, io-zstd
```

| Module | Purpose |
|--------|---------|
| `:app` | UI layer — Activities, Fragments, ViewModels, config |
| `:api` | Contracts — AIDL interfaces + plain Java interfaces (`IAppManager`, `IFileSystem`) |
| `:provider` | Root service implementation — `ProvidersImpl`, `AppManagerImpl`, `FileSystemProviderImpl` |
| `:hiddenapi` | Reflection-based access to hidden Android APIs via Rikka Refine |
| `:systemapi` | Stubs for Android framework internal classes |
| `:io-nativefs` | JNI/C++ native filesystem operations |
| `:io-tar` | JNI/C GNU tar archive read/write |
| `:io-zstd` | JNI/C ZSTD compression (bundled zstd-jni) |

## Key Architectural Patterns

**Root service IPC**: `api` module defines contracts, `provider` implements them via libsu. The app module accesses root services through the `Providers` interface — never touches root internals directly. Boot sequence: `SnapshotApp.onCreate()` → MMKV init → `ProvidersImpl` creation → `Shell.getShell().isRoot` check → `bindRootService()`.

**Hybrid AIDL/plain interfaces**: Core app management (`IAppManager`) and filesystem ops (`IFileSystem`) use plain Java interfaces compiled in-process. The compression pipeline (`IFileCompressor`) uses AIDL because it needs async callbacks (`ICompressCallback`) and cancellable tasks (`ITaskHandler`).

**Compression pipeline**: App data → `IFileSystem.createTarArchive()` (JNI tar) → `IFileCompressor.compress()` (zstd) → `.tar.zst`. Supports streaming via FIFO pipes (`mkfifo`) and `ParcelFileDescriptor`-based I/O to avoid intermediate files.

**Config**: MMKV is the sole persistence mechanism. `GlobalConfig` (Kotlin object singleton) stores group ID ordering and timeline filter presets (`timelinePreset`, `timelineCustomStart`, `timelineCustomEnd`). Per-group config uses separate MMKV instances; `group.json` `name` may be absent — `SnapGroup.name` falls back to directory basename then group `id`.

**ViewModels**: `SnapshotApp` instantiates `SnapshotViewModel` directly in `onCreate()` (not via `ViewModelProvider`) and exposes it as a top-level property via `SingletonViewModelFactory`. **Do not use `viewModelScope` on `SnapshotViewModel` for data loading** — Activity `onCleared()` cancels the scope while the singleton instance survives; group/app list mutations (`loadGroups`, `addGroup`, `deleteGroup`, `addAppsToGroup`) run on `AppDataRepository`'s process-level `CoroutineScope` (`SupervisorJob + Dispatchers.IO`) with `loadGroupsMutex` for serial reloads. `AppsViewModel` filters the app list from `SnapshotViewModel` using multi-dimensional filters. `TimelineViewModel` queries in-memory snapshots from `groupList` by time range; `navigateToGroup` on `SnapshotViewModel` scrolls the archive tab to a group when invoked from the timeline tab.

**Timeline tab**: Bottom nav order is `存档 | 时间线 | 应用`. Implementation lives under `app/.../main/timeline/` — see `docs/systems/timeline/INDEX.md`.

**Main shell UI**: `MainActivity` uses a `ConstraintLayout` host (`@id/coordinator`) with a fixed compact `MaterialToolbar` (`toolbar_height` 48dp, title via Navigation), fragment content below `toolbar_header`, and a floating `BlurView` bottom nav (`FloatingBottomNav`) built from a fixed-width `LinearLayout` + three equal `ImageButton` tabs (not `BottomNavigationView` — avoids landscape re-layout gaps). Navigation is wired in `setupBottomNavigation()`; cross-tab jumps use `selectBottomNavTab()`. No collapsing/large-title app bar — list scroll does not affect the toolbar. Both `MainActivity` and `SettingsActivity` declare `android:screenOrientation="portrait"` in the manifest — the UI is portrait-only; this avoids configuration-change recreation and dialog loss during rotation. See `docs/guides/getting-started/ui-shell.md`.

**Collapsible search**: List filter screens share `layout_search_field` + `CollapsibleSearchController` — filter row right-side `AppCompatImageButton` (`Widget.AppSnapshot.FilterRowIcon` 32dp on apps tab; `FilterToolbarIcon` 44dp on timeline / ignore-apps) toggles the search field. Horizontal inset: `filter_horizontal_padding` (12dp) start, `filter_section_inset_end` (8dp) end. Styles: `Widget.AppSnapshot.SearchField`.

**Apps filter row**: User `TabLayout` + system/user icon toggles + search share one row (`layout_apps_filter_row.xml`), hosted in `apps_filter_header` with tags below (`filter_row_section_gap` 8dp). System/user filters use `AppFilterHelper.setupFilterIconToggles` on `FilterRowIcon.Toggle` (32dp, `fitCenter`).

**Tag filter chips**: `TagsFilterLayout` renders scrollable tag chips and expand/collapse on the **same row** (`layout_tags_filter.xml`). Use `Widget.AppSnapshot.Chip.Tag` (22dp min height, 11sp). Each dynamic Chip must have a unique `View.id` for `ChipGroup` selection.

**Internationalization**: All user-facing copy in `:app` lives in `res/values/strings.xml` (default zh) with matching `values-zh-rCN/strings.xml` and `values-en/strings.xml`. Use `@string/` in XML and `getString()` in Kotlin — no hardcoded UI strings. Key names are snake_case with feature prefixes (`group_batch_*`, `provider_check_*`, etc.). `Log.*`, `tools:text` previews, and `provider` technical exceptions are not localized. See `docs/guides/getting-started/i18n.md`.

## Documentation System

The project has a two-layer documentation system:

- **Knowledge layer** (`docs/`): Architecture, systems, modules, guides, templates — stable, cross-module knowledge. Entry point: [`docs/INDEX.md`](docs/INDEX.md)
- **Action layer** (`dev/`): Progress, plans, decisions, roadmap — frequently updated development tracking
- **Design philosophy**: [`DESIGN.md`](DESIGN.md) — core principles and trade-offs
- **Glossary**: [`docs/glossary.md`](docs/glossary.md) — terminology definitions

Reading path: `AGENTS.md` → `DESIGN.md` → `docs/architecture/overview.md` → target module INDEX

## Conventions

- Kotlin for all new code in `app` and `provider`; Java interfaces in `api` are intentional (AIDL compatibility)
- ViewBinding + DataBinding for UI — do not add Compose
- JSON: FastJSON2 is the primary library; Moshi and Gson are also available
- Image loading: Glide (with kapt annotation processor in `app`)
- Async: Kotlin Coroutines — `AppDataRepository.scope` for global snapshot data; per-screen ViewModels (`AppsViewModel`, `TimelineViewModel`, `LauncherViewModel`) may use `viewModelScope` + `Dispatchers.IO`
- The `api` module must remain free of implementation details — only interfaces and data classes
- Native modules (`io-*`) each have their own `CMakeLists.txt` under `src/main/jni/`
