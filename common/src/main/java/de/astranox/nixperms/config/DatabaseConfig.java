package de.astranox.nixperms.config;

import de.astranox.nixperms.config.lib.NixConfigDaemon;
import de.astranox.nixperms.config.lib.NixConfiguration;
import de.astranox.nixperms.config.lib.Value;

@NixConfiguration(file = "database.nix")
public class DatabaseConfig extends NixConfigDaemon {

    @Value(key = "database.type", def = "mariadb")
    private String type;

    @Value(key = "database.host", def = "localhost")
    private String host;

    @Value(key = "database.port", def = "3306")
    private int port;

    @Value(key = "database.database", def = "database")
    private String database;

    @Value(key = "database.username", def = "user")
    private String username;

    @Value(key = "database.password", def = "secret")
    private String password;

    public String getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
