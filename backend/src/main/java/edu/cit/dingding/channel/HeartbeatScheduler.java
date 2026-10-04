package edu.cit.dingding.channel;

import edu.cit.dingding.config.InstanceIdentity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The very first heartbeat is sent by StartupChannelInitializer, before
 * anything else. This keeps it going every 30s after that — Tiangge
 * treats an instance with no heartbeat in the last 90s as offline, so
 * missing a few in a row before this fires again is fine; missing this
 * job entirely (e.g. forgetting @EnableScheduling) is not.
 */
@Component
class HeartbeatScheduler {

    private final TiangeeClient client;
    private final InstanceIdentity instanceIdentity;

    HeartbeatScheduler(TiangeeClient client, InstanceIdentity instanceIdentity) {
        this.client = client;
        this.instanceIdentity = instanceIdentity;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    void sendHeartbeat() {
        try {
            client.heartbeat("orderhub", instanceIdentity.getStartedAt(), instanceIdentity.uptimeSeconds());
        } catch (RuntimeException e) {
            System.err.println("[channel] Heartbeat failed: " + e.getMessage());
        }
    }
}
