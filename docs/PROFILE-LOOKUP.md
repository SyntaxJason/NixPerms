# Mojang profile lookup

When a command targets a player name that is not loaded and not present in the NixPerms database,
NixPerms resolves the official Java profile asynchronously before creating the user record.

Resolution order:

1. UUID input
2. loaded NixPerms users
3. local SQL name index
4. Mojang/Minecraft Services profile lookup
5. create/load the NixPerms user using the returned UUID and canonical name

The lookup is not part of permission checks or tab completion. Concurrent requests for the same
name share one future. Positive responses are cached for six hours and negative responses for five
minutes. Invalid Minecraft names are rejected locally without a network request.

The primary endpoint is Minecraft Services. The legacy Mojang endpoint is used as a compatibility
fallback. Both requests have strict connect and request timeouts.

Networks running standalone `online-mode=false` without authenticated proxy forwarding should be
aware that Mojang returns the official online UUID, not the server's generated offline UUID.
