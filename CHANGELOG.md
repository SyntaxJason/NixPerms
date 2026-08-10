# Changelog

## Unreleased

- Resolve never-joined Java players through Mojang/Minecraft Services when a command edits them by name.
- Cache positive and negative profile lookups and deduplicate concurrent requests.
- Persist the canonical profile name together with the official UUID before applying permission changes.

- Replaced wildcard substring construction with an immutable character-trie permission index.
- Added a fast lowercase lookup path that avoids normalization allocations for normal permission checks.
- Published user model, context, permissions and metadata through one atomic immutable state.
- Split blocking storage work from CPU-heavy permission snapshot rebuilding.
- Parallelized loaded-user rebuilds after group changes while preserving completion semantics.
- Coalesced repeated sync updates for the same entity within each database poll batch.
- Added hierarchical permission-node tab completion with Paper permission discovery.
- Added context-aware user, primary-group, secondary-group and delete-permission suggestions.
- Added bounded Damerau-Levenshtein typo correction with prefix-first result ranking.
- Added resolver, suggestion-tree and sync-coalescing regression tests.

## 0.1.0

- Introduced immutable, lock-free effective permission snapshots.
- Added exact, hierarchical wildcard and explicit deny resolution.
- Separated same-role inheritance from primary/default-secondary composition.
- Added fixed global, server, world and server+world scopes.
- Added atomic persistent user and group editor APIs.
- Rebuilt attachments around namespaced keys, priorities and exact-handle invalidation.
- Preserved Bukkit `PermissionAttachment` behavior in the Paper permissible bridge.
- Added SQLite, MySQL, MariaDB and PostgreSQL storage through HikariCP.
- Added transactional database-outbox polling with startup race protection and retry.
- Added end-to-end network propagation tests between independent core instances.
- Added one-time migration of experimental global permission tables.
- Registered the API through `NixPermsProvider` and Bukkit ServicesManager.
- Added resolver, attachment, SQLite transaction, migration and sync retry tests.
- Added Bukkit/Paper, Velocity and BungeeCord adapters with native permission bridges.
- Refreshes Paper's client command tree when a loaded user's effective permissions change.
- Added a Maven universal-distribution module that emits one deployable JAR for all supported platforms.
- Kept platform APIs out of the shaded JAR and relocated embedded HikariCP and SnakeYAML packages.

## 0.1.0 Paper adapter update

- Added a native `paper-plugin.yml` descriptor while retaining `plugin.yml` for Spigot.
- Added a Paper `BasicCommand` adapter for `/nixperms` and `/nixp`.
- Reused the same platform-neutral command processor and hierarchical completions on both platforms.
- Isolated Paper-only classes from the Spigot class-loading path.
