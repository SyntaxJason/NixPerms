# Command completion fixes

- Online Paper/Bukkit players are now included even when their NixPerms user was not already named.
- The command sender is always included in user suggestions.
- Paper startup synchronizes names for players who were already online.
- Permission discovery now reads Bukkit permissions, plugin descriptor declarations, child nodes and command permissions.
- Permission branches end in a dot, allowing repeated Tab presses to navigate the permission tree.
- Usage output uses a bold white label, cyan command path and white arguments instead of one flat white line.
