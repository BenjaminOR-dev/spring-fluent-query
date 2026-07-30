# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.1] — 2026-07-30

### Changed

- Projections use the same terminal names as entities, with an optional `Class` argument (Class **last**):
  - `first(Class)` / `firstOrFail(Class)` / `firstOrNull(Class)`
  - `one(Class)` / `oneOrFail(Class)` / `oneOrNull(Class)`
  - `latest(col, Class)` / `latestOrFail` / `latestOrNull` (+ metamodel)
  - `oldest(col, Class)` / `oldestOrFail` / `oldestOrNull` (+ metamodel)
  - `get(Class)` / `page(pageable, Class)` / `paginate(page, size, Class)` / `slice(pageable, Class)`
- Docs: entity vs projection vs FluentMap; `whereHas` + `exists`/`count`
- Example module: `GET /api/demos/{id}` returns a lean projection (`oneOrFail(Class)`)

### Deprecated

- `*As` / `*AsOrFail` / `*AsOrNull` aliases (`firstAs`, `oneAs`, `getAs`, `pageAs`, `paginateAs`, `sliceAs`, `latestAs*`, `oldestAs*`) — thin wrappers; prefer Class overloads

## [0.2.0]

See git history / previous release notes on Maven Central.
