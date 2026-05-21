# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

Build uses Gradle 8.13, AGP 8.13.2, Kotlin 2.2.0, Java 17, compileSdk 36, minSdk 28. NDK 25.2.9519653 with CMake 3.22.1 for native builds.

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

**Config**: MMKV is the sole persistence mechanism. `GlobalConfig` (Kotlin object singleton) stores group ID ordering. Per-group config uses separate MMKV instances. Room is used only in the `provider` module for other data.

**ViewModels**: `SnapshotApp` instantiates `SnapshotViewModel` directly in `onCreate()` (not via `ViewModelProvider`) and exposes it as a top-level property. `AppsViewModel` filters the app list from `SnapshotViewModel` using multi-dimensional filters.

## Conventions

- Kotlin for all new code in `app` and `provider`; Java interfaces in `api` are intentional (AIDL compatibility)
- ViewBinding + DataBinding for UI — do not add Compose
- JSON: FastJSON2 is the primary library; Moshi and Gson are also available
- Image loading: Glide (with kapt annotation processor in `app`)
- Async: Kotlin Coroutines on `viewModelScope` + `Dispatchers.IO`
- The `api` module must remain free of implementation details — only interfaces and data classes
- Native modules (`io-*`) each have their own `CMakeLists.txt` under `src/main/jni/`
