package de.astranox.nixperms.web;

import de.astranox.nixperms.core.config.WebConfig;
import de.astranox.nixperms.core.util.ServerAddressResolver;
import de.astranox.nixperms.core.web.WebEditorBridge;
import de.astranox.nixperms.web.auth.SessionManager;

import java.util.UUID;

public final class WebEditorBridgeImpl implements WebEditorBridge {

    private final NixWebServer server;
    private final WebConfig config;
    private final SessionManager sessions;
    private final String address;

    public WebEditorBridgeImpl(NixWebServer server, WebConfig config, SessionManager sessions) {
        this.server = server;
        this.config = config;
        this.sessions = sessions;
        this.address = ServerAddressResolver.resolve(config);
    }

    @Override public boolean isRunning() { return server.isRunning(); }
    @Override public int port() { return config.port; }
    @Override public String serverAddress() { return address; }

    @Override
    public String createSessionToken(UUID playerId) {
        return sessions.generate(playerId).token();
    }
}
