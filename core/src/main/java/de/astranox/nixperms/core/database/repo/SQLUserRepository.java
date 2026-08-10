package de.astranox.nixperms.core.database.repo;

import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.permission.PermissionScope;
import de.astranox.nixperms.core.database.SqlDialect;
import de.astranox.nixperms.core.model.UserModel;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SQLUserRepository {

    private static final String SELECT_USER = "SELECT uuid,name,primary_group,secondary_group FROM nixperms_users";

    private final SqlDialect dialect;

    public SQLUserRepository(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public @Nullable UserModel get(Connection connection, UUID uniqueId) throws SQLException {
        return find(connection, "uuid=?", uniqueId.toString());
    }

    public @Nullable UserModel getByName(Connection connection, String name) throws SQLException {
        return find(connection, "name_lower=?", name.trim().toLowerCase(Locale.ROOT));
    }

    public void save(Connection connection, UserModel model) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.upsertUser())) {
            statement.setString(1, model.uniqueId().toString());
            statement.setString(2, model.name());
            statement.setString(3, model.name() == null ? null : model.name().toLowerCase(Locale.ROOT));
            statement.setString(4, model.primaryGroupName());
            statement.setString(5, model.secondaryGroupName());
            statement.executeUpdate();
        }
        replaceRules(connection, model);
    }

    private @Nullable UserModel find(Connection connection, String condition, String value) throws SQLException {
        UUID uniqueId;
        String name;
        String primary;
        String secondary;
        try (PreparedStatement statement = connection.prepareStatement(SELECT_USER + " WHERE " + condition)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                uniqueId = UUID.fromString(result.getString("uuid"));
                name = result.getString("name");
                primary = result.getString("primary_group");
                secondary = result.getString("secondary_group");
            }
        }
        return new UserModel(uniqueId, name, primary, secondary, loadRules(connection, uniqueId));
    }

    private List<PermissionRule> loadRules(Connection connection, UUID uniqueId) throws SQLException {
        List<PermissionRule> rules = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT node,value,server_scope,world_scope FROM nixperms_user_rules WHERE uuid=?"
        )) {
            statement.setString(1, uniqueId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rules.add(new PermissionRule(
                            result.getString("node"),
                            PermissionDecision.of(result.getBoolean("value")),
                            new PermissionScope(result.getString("server_scope"), result.getString("world_scope"))
                    ));
                }
            }
        }
        return rules;
    }

    private void replaceRules(Connection connection, UserModel model) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM nixperms_user_rules WHERE uuid=?"
        )) {
            statement.setString(1, model.uniqueId().toString());
            statement.executeUpdate();
        }
        if (model.rules().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO nixperms_user_rules (uuid,node,value,server_scope,world_scope) VALUES(?,?,?,?,?)"
        )) {
            for (PermissionRule rule : model.rules()) {
                statement.setString(1, model.uniqueId().toString());
                statement.setString(2, rule.node());
                statement.setBoolean(3, rule.decision().allowed());
                statement.setString(4, rule.scope().server());
                statement.setString(5, rule.scope().world());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
