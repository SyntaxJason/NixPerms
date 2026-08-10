package de.astranox.nixperms.web.endpoint.v1;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.annotation.http.*;
import de.astranox.nixperms.api.user.INixUser;
import de.astranox.nixperms.web.model.request.*;
import de.astranox.nixperms.web.model.response.UserResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.UUID;

@Endpoint("/api/v1/users")
public final class UsersEndpoint {

    private final INixPermsAPI api;

    public UsersEndpoint(INixPermsAPI api) { this.api = api; }

    @GET @Auth
    public void getLoaded(Context ctx) {
        ctx.json(api.users().loaded().stream().map(UserResponse::from).toList());
    }

    @GET @Auth
    public void get(Context ctx) {
        UUID uuid = UUID.fromString(ctx.pathParam("uuid"));
        api.users().fetchSnapshot(uuid).thenAccept(snap -> {
            if (snap == null) { ctx.status(HttpStatus.NOT_FOUND); return; }
            ctx.json(snap);
        }).join();
    }

    @PUT @Auth
    public void setGroup(Context ctx) {
        UUID uuid = UUID.fromString(ctx.pathParam("uuid"));
        UserSetGroupRequest req = ctx.bodyAsClass(UserSetGroupRequest.class);
        var group = api.groups().group(req.groupName());
        if (group == null) { ctx.status(HttpStatus.NOT_FOUND); return; }
        api.users().loadUser(uuid).thenCompose(user -> ((INixUser) user).setPrimary(group).thenCompose(v -> api.users().saveUser((INixUser) user))).thenRun(() -> ctx.status(HttpStatus.NO_CONTENT)).join();
    }

    @PUT @Auth
    public void setPermission(Context ctx) {
        UUID uuid = UUID.fromString(ctx.pathParam("uuid"));
        UserSetPermissionRequest req = ctx.bodyAsClass(UserSetPermissionRequest.class);
        api.users().loadUser(uuid).thenCompose(user -> ((INixUser) user).setPermission(req.node(), req.value())).thenRun(() -> ctx.status(HttpStatus.NO_CONTENT)).join();
    }
}
