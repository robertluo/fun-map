# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

fun-map is a Clojure/ClojureScript library that provides a map data structure where values are automatically unwrapped when accessed. It supports lazy evaluation, dependency injection between map values, and lifecycle management.

## Build & Test Commands

```bash
# Run tests in watch mode (auto-reload on changes)
clj -M:dev:test --watch

# Run all tests once (CLJ + CLJS)
clj -T:build tests

# Run Clojure tests only
clj -M:dev:test

# Run ClojureScript tests only
clj -M:dev:cljs-test

# Build JAR
clj -T:build ci

# Deploy to Clojars
clj -T:build deploy

# Copy clj-kondo config to local dev
clj -T:build copy-clj-kondo-config
```

## Architecture

### Core Concepts

1. **Value Wrappers** (`wrapper.cljc`): Protocol `ValueWrapper` defines how values are unwrapped. Implementations include:
   - `FunctionWrapper` - wraps a function that receives the map and key
   - `CachedWrapper` - caches results with optional focus function for invalidation
   - `TracedWrapper` - adds tracing/logging capability

2. **DelegatedMap** (`core.cljc`): The main map implementation that:
   - Delegates to an underlying map
   - Applies `fn-entry` transformation on access to unwrap values
   - Implements full Clojure map interfaces for both CLJ and CLJS

3. **Macros** (`fun_map.cljc`):
   - `fw` - Creates function wrappers with full destructuring and options (`:focus`, `:trace`, `:par?`, `:wrappers`)
   - `fnk` - Shortcut for `fw` that auto-focuses on declared keys

### Key Files

- `src/robertluo/fun_map.cljc` - Public API: `fun-map`, `fw`, `fnk`, `life-cycle-map`, `closeable`, `halt!`
- `src/robertluo/fun_map/core.cljc` - `DelegatedMap` type with CLJ/CLJS implementations
- `src/robertluo/fun_map/wrapper.cljc` - `ValueWrapper` protocol and wrapper types
- `src/robertluo/fun_map/helper.cljc` - Macro helpers for `fw`/`fnk` expansion

### Testing

Tests use kaocha with cloverage. Test files mirror source structure in `test/`.

## Known Issues & Improvements

See `doc/improvements.adoc` for a tracked list of potential improvements.

### Key Gotchas

1. **~~Missing keys produce cryptic errors~~** - FIXED: now throws helpful `ex-info` with context
2. **~~Circular deps cause StackOverflowError~~** - FIXED: now detects cycles and reports the path
3. **Plain maps inside fun-maps don't unwrap** - use nested fun-maps if you need nested unwrapping
4. **Iteration realizes all values** - `keys`, `vals`, `seq` trigger all computations
5. **`select-keys`/`into` return plain maps** - wrappers are lost
6. **`:keep-ref true` + `fnk`** - `fnk` receives atoms, not values; use `fw` with explicit `@` in focus
7. **`update` on computed key** - replaces `fnk` with the updated value
