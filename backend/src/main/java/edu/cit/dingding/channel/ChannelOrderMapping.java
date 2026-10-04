package edu.cit.dingding.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The only place a Tiangge order ID is ever stored — Order/Inventory never
 * see it. Also doubles as the "each Tiangge order becomes exactly one
 * order" guard: tiangge_order_id is UNIQUE (see the SQL script), so a
 * second insert attempt for the same order fails loudly instead of quietly
 * creating a duplicate.
 */
@Entity
@Table(name = "channel_order_mappings")
class ChannelOrderMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tiangge_order_id")
    private String tiangeOrderId;

    @Column(name = "our_order_id")
    private Long ourOrderId;

    @Column(name = "last_decision")
    private String lastDecision;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ChannelOrderMapping() {
    }

    ChannelOrderMapping(String tiangeOrderId, Long ourOrderId, String lastDecision) {
        this.tiangeOrderId = tiangeOrderId;
        this.ourOrderId = ourOrderId;
        this.lastDecision = lastDecision;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    String getTiangeOrderId() { return tiangeOrderId; }
    Long getOurOrderId() { return ourOrderId; }
    String getLastDecision() { return lastDecision; }

    void setLastDecision(String lastDecision) {
        this.lastDecision = lastDecision;
        this.updatedAt = Instant.now();
    }
}
