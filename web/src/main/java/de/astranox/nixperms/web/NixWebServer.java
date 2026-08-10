package de.astranox.nixperms.web;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.api.event.group.*;
import de.astranox.nixperms.core.config.WebConfig;
import de.astranox.nixperms.web.auth.SessionManager;
import de.astranox.nixperms.web.endpoint.v1.*;
import de.astranox.nixperms.web.processor.AnnotationEndpointProcessor;
import de.astranox.nixperms.web.ws.WebSocketHandler;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class NixWebServer {

    private final Javalin app;
    private final SessionManager sessionManager;
    private final WebConfig config;
    private volatile boolean running = false;

    public NixWebServer(INixPermsAPI api, WebConfig config) {
        this.config = config;
        this.sessionManager = new SessionManager(config.sessionTtlMinutes);
        this.app = Javalin.create(cfg -> {
            cfg.staticFiles.add(sf -> { sf.hostedPath = "/webeditor"; sf.directory = "/webeditor"; sf.location = Location.CLASSPATH; });
        });
        new AnnotationEndpointProcessor(sessionManager).register(app, new GroupsEndpoint(api), new UsersEndpoint(api));
        app.get("/webeditor/{path}", ctx -> ctx.redirect("/webeditor/index.html"));
        app.ws("/ws/live", ws -> {
            ws.onConnect(ctx -> WebSocketHandler.onConnect(ctx, api, sessionManager));
            ws.onClose(ctx -> WebSocketHandler.onClose(ctx));
        });
        registerEventHandlers(api);
        scheduleSessionCleanup();
    }

    public void start(int port) { app.start(port); running = true; }
    public void stop() { app.stop(); running = false; }
    public boolean isRunning() { return running; }
    public int port() { return config.port; }
    public WebConfig config() { return config; }
    public SessionManager sessions() { return sessionManager; }

    private void registerEventHandlers(INixPermsAPI api) {
        api.events().subscribe(GroupEvent.class, event -> {
            switch (event) {
                case GroupCreateEvent e -> WebSocketHandler.broadcast(Map.of("type", "GROUP_CREATE", "group", e.group().name()));
                case GroupDeleteEvent e -> WebSocketHandler.broadcast(Map.of("type", "GROUP_DELETE", "group", e.groupName()));
                case GroupPermissionChangeEvent e -> WebSocketHandler.broadcast(Map.of("type", "GROUP_PERMISSION_CHANGE", "group", e.group().name(), "node", e.node(), "value", String.valueOf(e.newValue())));
                case GroupMetaChangeEvent e -> WebSocketHandler.broadcast(Map.of("type", "GROUP_META_CHANGE", "group", e.group().name(), "change", e.changeType().name()));
            }
        });
    }

    private void scheduleSessionCleanup() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "nixperms-session-cleanup"); t.setDaemon(true); return t; });
        scheduler.scheduleAtFixedRate(sessionManager::purgeExpired, 10, 10, TimeUnit.MINUTES);
    }
}
