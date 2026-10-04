package edu.cit.dingding.channel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

@Component
class ChannelFeedLock {

    private static final long LOCK_KEY = 0x4F52444552485542L;

    private final DataSource dataSource;

    ChannelFeedLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void runIfAcquired(Runnable action) {
        try (Connection connection = dataSource.getConnection()) {
            if (!acquire(connection)) {
                return;
            }

            try {
                action.run();
            } finally {
                release(connection);
            }
        } catch (SQLException e) {
            System.err.println("[channel] Could not acquire feed lock: " + e.getMessage());
        }
    }

    private boolean acquire(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private void release(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    System.err.println("[channel] Feed lock was not held when releasing it");
                }
            }
        } catch (SQLException e) {
            System.err.println("[channel] Could not release feed lock: " + e.getMessage());
        }
    }
}