package de.astranox.nixperms.core.database;

import java.util.Locale;

public enum SqlDialect {
    MYSQL,
    MARIADB,
    POSTGRESQL,
    SQLITE;

    public static SqlDialect from(String backend) {
        if (backend == null) throw new IllegalArgumentException("Database backend cannot be null");
        return switch (backend.trim().toLowerCase(Locale.ROOT)) {
            case "mysql" -> MYSQL;
            case "mariadb", "maria" -> MARIADB;
            case "postgresql", "postgres", "pgsql" -> POSTGRESQL;
            case "sqlite" -> SQLITE;
            default -> throw new IllegalArgumentException("Unknown database backend: " + backend);
        };
    }

    public String boolType() {
        return switch (this) {
            case POSTGRESQL -> "BOOLEAN";
            case SQLITE -> "INTEGER";
            case MYSQL, MARIADB -> "TINYINT(1)";
        };
    }

    public String syncIdDefinition() {
        return switch (this) {
            case POSTGRESQL -> "BIGSERIAL PRIMARY KEY";
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL, MARIADB -> "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        };
    }

    public String upsertGroup() {
        return switch (this) {
            case MYSQL, MARIADB -> "INSERT INTO nixperms_groups " +
                    "(name,role,weight,parent_name,default_secondary) VALUES(?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE role=VALUES(role),weight=VALUES(weight)," +
                    "parent_name=VALUES(parent_name),default_secondary=VALUES(default_secondary)";
            case POSTGRESQL, SQLITE -> "INSERT INTO nixperms_groups " +
                    "(name,role,weight,parent_name,default_secondary) VALUES(?,?,?,?,?) " +
                    "ON CONFLICT(name) DO UPDATE SET role=excluded.role,weight=excluded.weight," +
                    "parent_name=excluded.parent_name,default_secondary=excluded.default_secondary";
        };
    }

    public String upsertUser() {
        return switch (this) {
            case MYSQL, MARIADB -> "INSERT INTO nixperms_users " +
                    "(uuid,name,name_lower,primary_group,secondary_group) VALUES(?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE name=VALUES(name),name_lower=VALUES(name_lower),primary_group=VALUES(primary_group)," +
                    "secondary_group=VALUES(secondary_group)";
            case POSTGRESQL, SQLITE -> "INSERT INTO nixperms_users " +
                    "(uuid,name,name_lower,primary_group,secondary_group) VALUES(?,?,?,?,?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,name_lower=excluded.name_lower,primary_group=excluded.primary_group," +
                    "secondary_group=excluded.secondary_group";
        };
    }
}
