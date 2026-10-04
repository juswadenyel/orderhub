package edu.cit.dingding.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Dedup ledger: one row per Tiangge eventId we've ever processed. "Delivery
 * is at least once... process each eventId once" — this table is how.
 */
@Entity
@Table(name = "channel_events")
class ChannelEventRecord {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ChannelEventRecord() {
    }

    ChannelEventRecord(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    String getEventId() {
        return eventId;
    }
}
