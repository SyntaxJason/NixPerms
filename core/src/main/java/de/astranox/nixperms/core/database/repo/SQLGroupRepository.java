package de.astranox.nixperms.core.database.repo;

import de.astranox.nixperms.api.group.GroupRole;
import de.astranox.nixperms.api.permission.PermissionDecision;
import de.astranox.nixperms.api.permission.PermissionRule;
import de.astranox.nixperms.api.permission.PermissionScope;
import de.astranox.nixperms.core.database.SqlDialect;
import de.astranox.nixperms.core.model.GroupModel;
import de.astranox.nixperms.core.model.MetaEntryModel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SQLGroupRepository {

    private final SqlDialect dialect;

    public SQLGroupRepository(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public GroupModel get(Connection connection, String name) throws SQLException {
        GroupAccumulator group;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name,role,weight,parent_name,default_secondary FROM nixperms_groups WHERE name=?"
        )) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                group = accumulator(result);
            }
        }
        loadRules(connection, Map.of(name, group), " WHERE group_name=?", name);
        loadMeta(connection, Map.of(name, group), " WHERE group_name=?", name);
        loadOptions(connection, Map.of(name, group), " WHERE group_name=?", name);
        return group.build();
    }

    public Collection<GroupModel> getAll(Connection connection) throws SQLException {
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name,role,weight,parent_name,default_secondary FROM nixperms_groups"
        ); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                GroupAccumulator group = accumulator(result);
                groups.put(group.name, group);
            }
        }
        if (groups.isEmpty()) return List.of();
        loadRules(connection, groups, "", null);
        loadMeta(connection, groups, "", null);
        loadOptions(connection, groups, "", null);
        return groups.values().stream().map(GroupAccumulator::build).toList();
    }

    public void save(Connection connection, GroupModel model) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.upsertGroup())) {
            statement.setString(1, model.name());
            statement.setString(2, model.role().name());
            statement.setInt(3, model.weight());
            statement.setString(4, model.parentName());
            statement.setString(5, model.defaultSecondaryName());
            statement.executeUpdate();
        }
        deleteChildren(connection, model.name());
        insertRules(connection, model);
        insertMeta(connection, model);
        insertOptions(connection, model);
    }

    public void delete(Connection connection, String name) throws SQLException {
        deleteChildren(connection, name);
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM nixperms_groups WHERE name=?"
        )) {
            statement.setString(1, name);
            statement.executeUpdate();
        }
    }

    private void loadRules(
            Connection connection,
            Map<String, GroupAccumulator> groups,
            String suffix,
            String parameter
    ) throws SQLException {
        String sql = "SELECT group_name,node,value,server_scope,world_scope FROM nixperms_group_rules" + suffix;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    GroupAccumulator group = groups.get(result.getString("group_name"));
                    if (group == null) continue;
                    group.rules.add(new PermissionRule(
                            result.getString("node"), PermissionDecision.of(result.getBoolean("value")),
                            new PermissionScope(result.getString("server_scope"), result.getString("world_scope"))
                    ));
                }
            }
        }
    }

    private void loadMeta(
            Connection connection,
            Map<String, GroupAccumulator> groups,
            String suffix,
            String parameter
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT group_name,type,priority,value FROM nixperms_group_meta" + suffix
        )) {
            if (parameter != null) statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    GroupAccumulator group = groups.get(result.getString("group_name"));
                    if (group == null) continue;
                    MetaEntryModel entry = new MetaEntryModel(result.getInt("priority"), result.getString("value"));
                    if ("prefix".equalsIgnoreCase(result.getString("type"))) group.prefixes.add(entry);
                    if ("suffix".equalsIgnoreCase(result.getString("type"))) group.suffixes.add(entry);
                }
            }
        }
    }

    private void loadOptions(
            Connection connection,
            Map<String, GroupAccumulator> groups,
            String suffix,
            String parameter
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT group_name,key_name,value FROM nixperms_group_options" + suffix
        )) {
            if (parameter != null) statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    GroupAccumulator group = groups.get(result.getString("group_name"));
                    if (group != null) group.options.put(result.getString("key_name"), result.getString("value"));
                }
            }
        }
    }

    private void deleteChildren(Connection connection, String name) throws SQLException {
        for (String table : List.of("nixperms_group_rules", "nixperms_group_meta", "nixperms_group_options")) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE group_name=?")) {
                statement.setString(1, name);
                statement.executeUpdate();
            }
        }
    }

    private void insertRules(Connection connection, GroupModel model) throws SQLException {
        if (model.rules().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO nixperms_group_rules (group_name,node,value,server_scope,world_scope) VALUES(?,?,?,?,?)"
        )) {
            for (PermissionRule rule : model.rules()) {
                statement.setString(1, model.name());
                statement.setString(2, rule.node());
                statement.setBoolean(3, rule.decision().allowed());
                statement.setString(4, rule.scope().server());
                statement.setString(5, rule.scope().world());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertMeta(Connection connection, GroupModel model) throws SQLException {
        if (model.prefixes().isEmpty() && model.suffixes().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO nixperms_group_meta (group_name,type,priority,value) VALUES(?,?,?,?)"
        )) {
            addMetaBatch(statement, model.name(), "prefix", model.prefixes());
            addMetaBatch(statement, model.name(), "suffix", model.suffixes());
            statement.executeBatch();
        }
    }

    private void addMetaBatch(
            PreparedStatement statement,
            String group,
            String type,
            List<MetaEntryModel> entries
    ) throws SQLException {
        for (MetaEntryModel entry : entries) {
            statement.setString(1, group);
            statement.setString(2, type);
            statement.setInt(3, entry.priority());
            statement.setString(4, entry.value());
            statement.addBatch();
        }
    }

    private void insertOptions(Connection connection, GroupModel model) throws SQLException {
        if (model.options().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO nixperms_group_options (group_name,key_name,value) VALUES(?,?,?)"
        )) {
            for (Map.Entry<String, String> entry : model.options().entrySet()) {
                statement.setString(1, model.name());
                statement.setString(2, entry.getKey());
                statement.setString(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private GroupAccumulator accumulator(ResultSet result) throws SQLException {
        return new GroupAccumulator(
                result.getString("name"), GroupRole.valueOf(result.getString("role")),
                result.getInt("weight"), result.getString("parent_name"),
                result.getString("default_secondary")
        );
    }

    private static final class GroupAccumulator {
        private final String name;
        private final GroupRole role;
        private final int weight;
        private final String parent;
        private final String defaultSecondary;
        private final List<PermissionRule> rules = new ArrayList<>();
        private final List<MetaEntryModel> prefixes = new ArrayList<>();
        private final List<MetaEntryModel> suffixes = new ArrayList<>();
        private final Map<String, String> options = new LinkedHashMap<>();

        private GroupAccumulator(String name, GroupRole role, int weight, String parent, String defaultSecondary) {
            this.name = name;
            this.role = role;
            this.weight = weight;
            this.parent = parent;
            this.defaultSecondary = defaultSecondary;
        }

        private GroupModel build() {
            return new GroupModel(name, role, weight, parent, defaultSecondary, rules, prefixes, suffixes, options);
        }
    }
}
