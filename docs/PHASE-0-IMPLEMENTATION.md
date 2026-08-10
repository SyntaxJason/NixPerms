# Phase 0 implementation notes

## Permission lookup

`NixPermissionData` now builds a `PermissionNodeIndex` once per immutable snapshot. The
index stores exact decisions and wildcard fallback decisions separately. During lookup it
walks the input characters once and remembers the deepest wildcard encountered. Exact
rules still win at the terminal node.

Typical lowercase permission strings are used directly. Trimming and lowercasing only
occur when the input actually requires normalization.

## Atomic user state

`NixUser` publishes one immutable state record instead of independent volatile fields for
model, context and cache. Snapshot calculations capture the state revision and may publish
only when that revision is still current. A concurrent edit or world change therefore
cannot be overwritten by an older calculation.

## Executor model

- Storage executor: bounded by `database.threads`; performs JDBC and serialized mutations.
- Compute executor: sized from available processors; performs snapshot compilation and
  parallel group fan-out.
- Sync scheduler: only polls and sequences network invalidations.

Repeated context or attachment refresh requests for one user are coalesced into one worker.

## Command completion

Permission completion exposes one path level at a time. The catalog merges:

- Permission nodes registered in Bukkit/Paper.
- Rules owned by loaded groups.
- Rules and effective nodes of loaded users.
- NixPerms' own administration permission.

Normal prefixes are always preferred. Bounded Damerau-Levenshtein matching is only used
when no prefix result exists, keeping both latency and result noise low.

## Paper command adapter

- Added `paper-plugin.yml` while retaining `plugin.yml` for Spigot compatibility.
- Paper uses a native `BasicCommand` adapter and receives the same command execution and suggestion arrays as Bukkit.
- Spigot continues to use `CommandExecutor` and `TabCompleter` from `plugin.yml`.
- Paper-only API classes are isolated behind `PaperRuntime`, preventing Spigot from loading them.
- The Paper command declares `nixperms.admin` as its root requirement, so command visibility is refreshed by the existing command-tree updater when effective permissions change.
