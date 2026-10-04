package edu.cit.dingding.channel;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.dingding.config.InstanceIdentity;

/**
 * "Your shop goes live on Tiangge once a running instance has sent a
 * heartbeat and you have published at least one listing" — this is that
 * startup sequence, in the exact order the manual asks for: heartbeat
 * first (before any other call), then listings, then current stock so
 * buyers actually see something orderable.
 */
@Component
class StartupChannelInitializer {

    private final TiangeeClient client;
    private final InstanceIdentity instanceIdentity;
    private final TiangeeChannelService channelService;
    private final FeedPoller feedPoller;

    StartupChannelInitializer(TiangeeClient client, InstanceIdentity instanceIdentity,
                               TiangeeChannelService channelService, FeedPoller feedPoller) {
        this.client = client;
        this.instanceIdentity = instanceIdentity;
        this.channelService = channelService;
        this.feedPoller = feedPoller;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            client.heartbeat("orderhub", instanceIdentity.getStartedAt(), instanceIdentity.uptimeSeconds());
            System.out.println("[channel] First heartbeat sent. Instance ID: " + instanceIdentity.getId());

            channelService.publishListings();
            System.out.println("[channel] Listings published.");

            channelService.publishAllCurrentStock();
            System.out.println("[channel] Initial stock published. Shop should now be live on Tiangge.");

            feedPoller.pollFeed();
        } catch (RuntimeException e) {
            // Don't crash the whole app over a flaky first call — the
            // scheduled heartbeat/feed-poll jobs will keep trying.
            System.err.println("[channel] Startup sequence failed, will keep retrying on schedule: " + e.getMessage());
        }
    }
}
