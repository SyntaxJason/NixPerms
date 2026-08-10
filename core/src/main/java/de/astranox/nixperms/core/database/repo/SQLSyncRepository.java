package de.astranox.nixperms.core.database.repo;

import de.astranox.nixperms.core.sync.SyncMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SQLSyncRepository {

    public void publish(Connection connection, SyncMessage message) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO nixperms_sync (server_id,type,payload,created_at) VALUES(?,?,?,?)"
        )) {
            statement.setString(1, message.serverId());
            statement.setString(2, message.type());
            statement.setString(3, message.payload());
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public List<SyncMessage> pollSince(
            Connection connection,
            long lastId,
            int limit
    ) throws SQLException {
        List<SyncMessage> messages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,server_id,type,payload FROM nixperms_sync " +
                        "WHERE id>? ORDER BY id ASC LIMIT ?"
        )) {
            statement.setLong(1, lastId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new SyncMessage(
                            result.getLong("id"), result.getString("server_id"),
                            result.getString("type"), result.getString("payload")
                    ));
                }
            }
        }
        return messages;
    }

    public long latestId(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(id),0) FROM nixperms_sync"
        ); ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    public int cleanup(Connection connection, long maxAgeMillis) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM nixperms_sync WHERE created_at<?"
        )) {
            statement.setLong(1, System.currentTimeMillis() - maxAgeMillis);
            return statement.executeUpdate();
        }
    }
}
