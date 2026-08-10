package de.astranox.nixperms.web.ws;

import de.astranox.nixperms.api.INixPermsAPI;
import de.astranox.nixperms.web.auth.SessionManager;
import de.astranox.nixperms.web.auth.SessionToken;
import io.javalin.websocket.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WebSocketHandler {

    private static final CopyOnWriteArrayList<WsContext> sessions = new CopyOnWriteArrayList<>();

    private WebSocketHandler() {}

    public static void onConnect(WsConnectContext ctx, INixPermsAPI api, SessionManager sessionManager) {
        String token = ctx.queryParam("token");
        if (sessionManager.validate(token).isEmpty()) { ctx.closeSession(4401, "Unauthorized"); return; }
        sessions.add(ctx);
    }

    public static void onClose(WsCloseContext ctx) {
        sessions.remove(ctx);
    }

    public static void broadcast(Map<String, Object> payload) {
        String json = toJson(payload);
        sessions.removeIf(s -> { try { if (!s.session.isOpen()) return true; s.send(json); return false; } catch (Exception e) { return true; } });
    }

    private static String toJson(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder("{");
        payload.forEach((k, v) -> { if (sb.length() > 1) sb.append(","); sb.append("\"").append(k).append("\":\"").append(v).append("\""); });
        sb.append("}");
        return sb.toString();
    }
}
