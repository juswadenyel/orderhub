package edu.cit.dingding.channel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelFeedLockTest {

    @Test
    void runsActionAndReleasesLockWhenAcquired() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquireStatement = mock(PreparedStatement.class);
        PreparedStatement releaseStatement = mock(PreparedStatement.class);
        ResultSet acquireResult = mock(ResultSet.class);
        ResultSet releaseResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(acquireStatement, releaseStatement);
        when(acquireStatement.executeQuery()).thenReturn(acquireResult);
        when(releaseStatement.executeQuery()).thenReturn(releaseResult);
        when(acquireResult.next()).thenReturn(true);
        when(acquireResult.getBoolean(1)).thenReturn(true);
        when(releaseResult.next()).thenReturn(true);
        when(releaseResult.getBoolean(1)).thenReturn(true);

        boolean[] ran = {false};
        new ChannelFeedLock(dataSource).runIfAcquired(() -> ran[0] = true);

        assertTrue(ran[0]);
        verify(releaseStatement).executeQuery();
        verify(connection).close();
    }

    @Test
    void skipsActionWhenAnotherInstanceOwnsTheLock() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquireStatement = mock(PreparedStatement.class);
        ResultSet acquireResult = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(acquireStatement);
        when(acquireStatement.executeQuery()).thenReturn(acquireResult);
        when(acquireResult.next()).thenReturn(true);
        when(acquireResult.getBoolean(1)).thenReturn(false);

        boolean[] ran = {false};
        new ChannelFeedLock(dataSource).runIfAcquired(() -> ran[0] = true);

        assertFalse(ran[0]);
        verify(connection).close();
        verify(acquireStatement, never()).executeUpdate();
    }
}