# NixPerms 0.1.0

NixPerms is a platform-neutral permission system built around predictable resolution and a very small hot path. Permission checks read one immutable in-memory character trie; database access, context filtering, inheritance traversal and attachment layering happen only when that snapshot is rebuilt. Exact and wildcard lookups do not construct temporary wildcard strings.

## Requirements

- Java 21
- Paper/Spigot 1.21.x, Velocity 3.4.x or BungeeCord 1.21
- SQLite for a single server, or MySQL/MariaDB/PostgreSQL for a network

The Maven reactor produces one universal plugin JAR. Copy that same JAR into every
supported server or proxy `plugins` directory; each platform selects only its own
descriptor and entry point. Paper-compatible forks such as Purpur should work through
the Bukkit adapter. Folia is not declared supported in 0.1.

## Group model

Every user has exactly one `PRIMARY` group and may have one explicit `SECONDARY` group. A primary group may also specify a default secondary group. Inheritance and primary/secondary composition are separate concepts:

- a group parent must have the same role;
- inheritance cycles are rejected;
- only primary groups may define a default secondary;
- a user’s explicit secondary overrides the primary group’s default secondary.

The configured resolution policy controls conflicts between primary and secondary groups: `PRIMARY_WINS`, `SECONDARY_WINS` or `DENY_WINS`.

Resolution order is:

1. parent group to child group;
2. primary and secondary groups using the configured policy;
3. persistent user rules;
4. runtime attachments ordered by priority and creation sequence.

Within one layer, an exact node wins over the nearest wildcard and then `*`. A higher layer overrides a lower layer even when the higher rule is a wildcard.

## Small context system

NixPerms has fixed server and world scopes rather than arbitrary context maps:

```java
editor.allow("chat.send");
editor.deny("chat.send", PermissionScope.server("lobby"));
editor.allow("chat.send", PermissionScope.world("event_world"));
editor.deny("chat.send", PermissionScope.serverWorld("lobby", "event_world"));
```

Matching scope precedence is global, server, world, then server+world. Context rules are flattened when a player joins or changes world; `hasPermission` does not evaluate contexts.

## API setup

Depend on the API as `provided`:

```xml
<dependency>
    <groupId>de.astranox.nixperms</groupId>
    <artifactId>nixperms-api</artifactId>
    <version>0.1.0</version>
    <scope>provided</scope>
</dependency>
```

Declare NixPerms as a platform dependency and obtain the API through the platform-neutral provider:

```java
INixPermsAPI api = NixPermsProvider.get();
```

Bukkit plugins may alternatively use the registered `ServicesManager` entry:

```java
RegisteredServiceProvider<INixPermsAPI> registration =
        Bukkit.getServicesManager().getRegistration(INixPermsAPI.class);
```

### Atomic persistent edits

Editors make one database write and publish one network invalidation after validation succeeds:

```java
api.groups().create("member", GroupRole.PRIMARY, group -> {
    group.weight(10);
    group.allow("server.join");
    group.deny("admin.*");
});

api.users().edit(playerId, user -> {
    user.primary("member");
    user.allow("homes.3");
});
```

The returned `CompletableFuture` completes after the database commit and local snapshot update. Do not block a platform event loop or server tick thread on it.

Persistent-edit continuations and NixPerms events may run on a database or sync executor. Schedule platform API access back onto the platform's scheduler when required; NixPerms' own command bridges already deliver replies safely.

### Runtime attachments

Attachments are runtime-only overlays for sessions, minigames and other temporary state:

```java
IPermissionAttachment boost = user.attachments().create(
        "myplugin:boost",
        100,
        rules -> {
            rules.allow("flight.use");
            rules.deny("flight.admin");
        }
);

boost.edit(rules -> rules.allow("flight.speed.fast"));
boost.close(); // removes exactly this handle
```

Attachment keys are namespaced (`plugin:id`). Creating the same key replaces the old handle atomically; closing that old handle cannot remove its replacement. Larger priorities win, and a later attachment wins when priorities are equal.

Cleanup is explicit and scoped:

```java
user.attachments().remove("myplugin:boost");
api.attachments().invalidateNamespace("myplugin");
```

Bukkit `PermissionAttachment`s are also preserved by the Paper bridge and resolve above persisted NixPerms data.

## Configuration

The default configuration uses local SQLite. For a network, configure the same remote database on every server, give every server a unique `server-id`, and enable polling:

```yaml
server-id: lobby-1

database:
  type: mariadb
  host: 127.0.0.1
  port: 3306
  name: nixperms
  username: nixperms
  password: change-me
  pool-size: 10
  threads: 4

network:
  enabled: true
  poll-interval-ms: 1000
  retention-seconds: 300
```

Network writes use a transactional database outbox: the permission change and its sync row commit together. A poll cursor is captured before the initial cache load to avoid startup races. Failed cache refreshes leave the message unacknowledged and retry it on the next poll. Repeated updates for the same user or group are coalesced inside one poll batch before caches are rebuilt.

SQLite is intentionally rejected when network polling is enabled because a local file is not a shared transport.

On Velocity and BungeeCord, the fixed `server` context becomes the connected backend
server name (for example `lobby`). Before a backend connection exists, it uses the
configured `server-id`. Use the exact proxy backend names when creating server-scoped
rules.

## Commands

The administration permission is `nixperms.admin` (operator by default).
Paper command visibility is refreshed automatically after effective permissions change,
including updates received through network polling. Players do not need operator status
or a reconnect to see newly available commands.

- `/nixperms group create|delete|addperm|delperm|setparent|clearparent|setdefaultsecondary|cleardefaultsecondary|setweight|info|list`
- `/nixperms user addperm|delperm|setgroup|setsecondary|clearsecondary|info|resolve`
- `/nixperms reload data`

Tab completion is hierarchical rather than a dump of every known node. For example,
`/nixperms user addperm <user> ` first suggests roots such as `essentials`, `minecraft`
and `nixperms`. Entering `essentials.` then exposes only its direct children. On Paper,
NixPerms discovers permission nodes registered with Bukkit and merges them with nodes
already stored in groups and loaded users. Damerau-Levenshtein matching also corrects
small typing mistakes such as `essentails` without flooding normal prefix results.
`delperm` narrows completion to permissions directly owned by the selected user or group.

Database and network configuration changes require a server restart. `reload data` reloads group data and rebuilds loaded user snapshots.

## Migration

At startup NixPerms creates the v0.1 schema and performs a one-time, non-destructive migration from the experimental `nixperms_group_permissions` and `nixperms_user_permissions` tables into global scoped rules. The legacy tables are left untouched.

Back up a production database before upgrading.

## Build and test

```bash
mvn clean verify
```

The deployable artifact is written to
`distribution/universal/target/NixPerms-0.1.0.jar`. The shade step embeds the core,
database drivers and all three platform adapters while leaving server APIs out of the
JAR. Unit and SQLite integration tests cover scope and wildcard precedence, attachment
replacement, legacy migration, transactional outbox rollback, polling retry and
end-to-end propagation between two running core instances. During `verify`, a separate
integration test also boots the relocated core from the finished universal JAR with an
isolated class loader and a real SQLite database.

## Current boundaries

- One universal JAR supports Bukkit/Paper, Velocity and BungeeCord; it must still be copied to each node separately.
- Folia and Fabric/Sponge server-side loaders are not supported in 0.1.
- Network synchronization currently uses the transactional database outbox. PostgreSQL notifications and optional NixLink relays are planned as accelerators, never mandatory infrastructure.
- The old experimental web editor is excluded until its API is rebuilt against the v0.1 model.
