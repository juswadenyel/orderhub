package edu.cit.dingding.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single row (id is always 1). Storing the feed cursor durably in Postgres
 * — not in memory — is what lets a restarted instance "continue from where
 * it stopped instead of starting over" (Task 4 / Stage 4 restart test).
 */
@Entity
@Table(name = "channel_cursor")
class ChannelCursor {

    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "last_seq")
    private long lastSeq;

    protected ChannelCursor() {
    }

    ChannelCursor(long lastSeq) {
        this.lastSeq = lastSeq;
    }

    Integer getId() { return id; }
    long getLastSeq() { return lastSeq; }
    void setLastSeq(long lastSeq) { this.lastSeq = lastSeq; }
}
