package de.astranox.nixperms.web.processor;

import de.astranox.nixperms.api.annotation.http.*;
import de.astranox.nixperms.web.auth.SessionManager;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.lang.reflect.Method;

public final class AnnotationEndpointProcessor {

    private final SessionManager sessionManager;

    public AnnotationEndpointProcessor(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void register(Javalin app, Object... endpoints) {
        for (Object endpoint : endpoints) {
            Endpoint endpointAnnotation = endpoint.getClass().getAnnotation(Endpoint.class);
            if (endpointAnnotation == null) continue;
            String basePath = endpointAnnotation.value();
            for (Method method : endpoint.getClass().getDeclaredMethods()) {
                method.setAccessible(true);
                Auth auth = method.getAnnotation(Auth.class);
                if (method.isAnnotationPresent(GET.class)) app.get(basePath, ctx -> handle(ctx, endpoint, method, auth));
                else if (method.isAnnotationPresent(POST.class)) app.post(basePath, ctx -> handle(ctx, endpoint, method, auth));
                else if (method.isAnnotationPresent(PUT.class)) app.put(basePath + "/{name}/permissions", ctx -> handle(ctx, endpoint, method, auth));
                else if (method.isAnnotationPresent(PATCH.class)) app.patch(basePath + "/{name}/parent", ctx -> handle(ctx, endpoint, method, auth));
                else if (method.isAnnotationPresent(DELETE.class)) app.delete(basePath + "/{name}", ctx -> handle(ctx, endpoint, method, auth));
            }
        }
    }

    private void handle(Context ctx, Object endpoint, Method method, Auth auth) throws Exception {
        if (auth != null && auth.strategy() == AuthStrategy.SESSION_TOKEN) {
            String bearer = ctx.header("Authorization");
            String token = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : ctx.queryParam("token");
            if (sessionManager.validate(token).isEmpty()) { ctx.status(HttpStatus.UNAUTHORIZED); return; }
        }
        method.invoke(endpoint, ctx);
    }
}
