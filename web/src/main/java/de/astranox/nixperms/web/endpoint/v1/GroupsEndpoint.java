package de.astranox.nixperms.web.endpoint.v1;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.annotation.http.*;
import de.astranox.nixperms.api.group.IPermissionGroup;
import de.astranox.nixperms.web.model.request.*;
import de.astranox.nixperms.web.model.response.GroupResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

@Endpoint("/api/v1/groups")
public final class GroupsEndpoint {

    private final INixPermsAPI api;

    public GroupsEndpoint(INixPermsAPI api) { this.api = api; }

    @GET @Auth
    public void getAll(Context ctx) {
        ctx.json(api.groups().loaded().stream().map(GroupResponse::from).toList());
    }

    @POST @Auth
    public void create(Context ctx) {
        GroupCreateRequest req = ctx.bodyAsClass(GroupCreateRequest.class);
        if (api.groups().group(req.name()) != null) { ctx.status(HttpStatus.CONFLICT); return; }
        api.groups().create(req.name(), req.role()).thenAccept(group -> ctx.json(GroupResponse.from(group))).join();
    }

    @DELETE @Auth
    public void delete(Context ctx) {
        String name = ctx.pathParam("name");
        IPermissionGroup group = api.groups().group(name);
        if (group == null) { ctx.status(HttpStatus.NOT_FOUND); return; }
        api.groups().delete(group).thenRun(() -> ctx.status(HttpStatus.NO_CONTENT)).join();
    }

    @PUT @Auth
    public void setPermission(Context ctx) {
        String name = ctx.pathParam("name");
        IPermissionGroup group = api.groups().group(name);
        if (group == null) { ctx.status(HttpStatus.NOT_FOUND); return; }
        GroupSetPermissionRequest req = ctx.bodyAsClass(GroupSetPermissionRequest.class);
        api.groups().setPermission(group, req.node(), req.value()).thenRun(() -> ctx.status(HttpStatus.NO_CONTENT)).join();
    }

    @PATCH @Auth
    public void setParent(Context ctx) {
        String name = ctx.pathParam("name");
        IPermissionGroup group = api.groups().group(name);
        if (group == null) { ctx.status(HttpStatus.NOT_FOUND); return; }
        GroupSetParentRequest req = ctx.bodyAsClass(GroupSetParentRequest.class);
        IPermissionGroup parent = req.parentName() != null ? api.groups().group(req.parentName()) : null;
        api.groups().setParent(group, parent).thenRun(() -> ctx.status(HttpStatus.NO_CONTENT)).join();
    }
}
