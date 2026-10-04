package edu.cit.dingding.channel;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.cit.dingding.shop.OrderAlreadyCancelledException;
import edu.cit.dingding.shop.OrderService;

class FeedPollerTest {

    @Test
    void confirmsBackorderedCancellationWithoutClaimingAStockRestock() {
        TiangeeClient client = mock(TiangeeClient.class);
        OrderService orderService = mock(OrderService.class);
        ChannelEventRepository eventRepository = mock(ChannelEventRepository.class);
        ChannelOrderMappingRepository orderMappingRepository = mock(ChannelOrderMappingRepository.class);
        ChannelCursorRepository cursorRepository = mock(ChannelCursorRepository.class);
        ChannelFeedLock feedLock = inlineFeedLock();
        ChannelCursor cursor = new ChannelCursor(0);
        TiangeeClient.FeedEvent cancellation = new TiangeeClient.FeedEvent(
                1, "event-1", "ORDER_CANCELLED", "TG-1", null, null, null, null, List.of());
        ChannelOrderMapping mapping = new ChannelOrderMapping("TG-1", 42L, "BACKORDERED");

        when(cursorRepository.findById(1)).thenReturn(Optional.of(cursor));
        when(client.getFeed(0, 50)).thenReturn(new TiangeeClient.FeedResponse(List.of(cancellation), 1));
        when(eventRepository.existsById("event-1")).thenReturn(false);
        when(orderMappingRepository.findByTiangeOrderId("TG-1")).thenReturn(Optional.of(mapping));
        when(orderService.cancelOrder(42L)).thenReturn(false);

        FeedPoller poller = new FeedPoller(client, orderService, mock(ChannelListingMapping.class), eventRepository,
                orderMappingRepository, cursorRepository, feedLock);
        poller.pollFeed();

        verify(client).confirmCancellation("TG-1", false);
    }

    @Test
    void cancellationRetryUsesTheOriginalReservationDecision() {
        TiangeeClient client = mock(TiangeeClient.class);
        OrderService orderService = mock(OrderService.class);
        ChannelEventRepository eventRepository = mock(ChannelEventRepository.class);
        ChannelOrderMappingRepository orderMappingRepository = mock(ChannelOrderMappingRepository.class);
        ChannelCursorRepository cursorRepository = mock(ChannelCursorRepository.class);
        ChannelFeedLock feedLock = inlineFeedLock();
        ChannelCursor cursor = new ChannelCursor(0);
        TiangeeClient.FeedEvent cancellation = new TiangeeClient.FeedEvent(
                1, "event-1", "ORDER_CANCELLED", "TG-1", null, null, null, null, List.of());
        ChannelOrderMapping mapping = new ChannelOrderMapping("TG-1", 42L, "ACCEPTED");

        when(cursorRepository.findById(1)).thenReturn(Optional.of(cursor));
        when(client.getFeed(0, 50)).thenReturn(new TiangeeClient.FeedResponse(List.of(cancellation), 1));
        when(eventRepository.existsById("event-1")).thenReturn(false);
        when(orderMappingRepository.findByTiangeOrderId("TG-1")).thenReturn(Optional.of(mapping));
        when(orderService.cancelOrder(42L))
                .thenReturn(true)
                .thenThrow(new OrderAlreadyCancelledException(42L));
        org.mockito.Mockito.doThrow(new RuntimeException("temporary failure"))
                .doNothing()
                .when(client).confirmCancellation("TG-1", true);

        FeedPoller poller = new FeedPoller(client, orderService, mock(ChannelListingMapping.class), eventRepository,
                orderMappingRepository, cursorRepository, feedLock);
        poller.pollFeed();
        poller.pollFeed();

        verify(client, org.mockito.Mockito.times(2)).confirmCancellation("TG-1", true);
    }

        private static ChannelFeedLock inlineFeedLock() {
                ChannelFeedLock feedLock = mock(ChannelFeedLock.class);
                doAnswer(invocation -> {
                        Runnable action = invocation.getArgument(0);
                        action.run();
                        return null;
                }).when(feedLock).runIfAcquired(any(Runnable.class));
                return feedLock;
        }
}