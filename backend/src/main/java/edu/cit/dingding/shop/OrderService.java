package edu.cit.dingding.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.dingding.inventory.InventoryItem;
import edu.cit.dingding.inventory.InventoryItemNotFoundException;
import edu.cit.dingding.inventory.InventoryService;
import edu.cit.dingding.shop.dto.InventorySnapshotDto;
import edu.cit.dingding.shop.dto.OrderItemResultDto;
import edu.cit.dingding.shop.dto.OrderLineItemDto;
import edu.cit.dingding.shop.dto.OrderLineSummaryDto;
import edu.cit.dingding.shop.dto.OrderResponseDto;
import edu.cit.dingding.shop.dto.OrderSummaryDto;
import edu.cit.dingding.shop.events.OrderPlacedEvent;
import edu.cit.dingding.shop.events.OrderRejectedEvent;
import edu.cit.dingding.supplier.SupplierGateway;

/**
 * Depends on InventoryService and (new in Lab 4) SupplierGateway — both
 * just interfaces, never their impls. Still has no idea Channel/Tiangge
 * exists; "backorder" is treated as a plain domain concept here, not a
 * Tiangge-specific one.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                         SupplierGateway supplierGateway,
                         OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
    }

    private record LineCheck(String productId, int quantity, boolean valid, String failureCode, String failureMessage) {
    }

    /** Lab 2 behavior, unchanged: used by the React UI. Never backorders. */
    @Transactional
    public OrderResponseDto placeOrder(List<OrderLineItemDto> lineItems) {
        return placeOrder(lineItems, false);
    }

    /**
     * Lab 4: used by the channel module for marketplace orders. When
     * allowBackorder is true and stock is short, checks whether every
     * short item already has an open supplier purchase order — if so,
     * BACKORDERED instead of REJECTED.
     */
    @Transactional
    public OrderResponseDto placeOrder(List<OrderLineItemDto> lineItems, boolean allowBackorder) {
        Map<String, Integer> requestedQuantities = new LinkedHashMap<>();
        for (OrderLineItemDto line : lineItems) {
            Integer existingQuantity = requestedQuantities.get(line.getProductId());
            int totalQuantity = existingQuantity == null
                    ? line.getQuantity()
                    : Math.addExact(existingQuantity, line.getQuantity());
            requestedQuantities.put(line.getProductId(), totalQuantity);
        }

        // Lock all requested inventory rows in a stable order before reading
        // availability. Concurrent marketplace orders then cannot both approve
        // the same final units based on an unlocked snapshot.
        requestedQuantities.keySet().stream().sorted().forEach(productId -> {
            try {
                inventoryService.lockItem(productId);
            } catch (InventoryItemNotFoundException ignored) {
                // The validation pass below returns the normal product error.
            }
        });

        List<LineCheck> checks = new ArrayList<>();
        boolean allValid = true;

        for (Map.Entry<String, Integer> requested : requestedQuantities.entrySet()) {
            try {
                InventoryItem item = inventoryService.getItem(requested.getKey());
                if (requested.getValue() > item.getStock()) {
                    allValid = false;
                    String msg = "Requested quantity (" + requested.getValue()
                            + ") exceeds available stock (" + item.getStock() + ") for " + item.getProductId();
                    checks.add(new LineCheck(requested.getKey(), requested.getValue(), false, "INSUFFICIENT_STOCK", msg));
                } else {
                    checks.add(new LineCheck(requested.getKey(), requested.getValue(), true, null, null));
                }
            } catch (InventoryItemNotFoundException ex) {
                allValid = false;
                checks.add(new LineCheck(requested.getKey(), requested.getValue(), false, "PRODUCT_NOT_FOUND", ex.getMessage()));
            }
        }

        boolean canBackorder = false;
        if (!allValid && allowBackorder) {
            // Backorder only if EVERY short item (insufficient stock, not
            // "product not found" — that's a data problem, never backorderable)
            // already has an open LegacySupply purchase order in flight.
            canBackorder = checks.stream()
                    .filter(c -> !c.valid())
                    .allMatch(c -> "INSUFFICIENT_STOCK".equals(c.failureCode())
                            && supplierGateway.hasOpenPurchaseOrder(c.productId()));
        }

        OrderStatus status = allValid ? OrderStatus.CONFIRMED
                : canBackorder ? OrderStatus.BACKORDERED
                : OrderStatus.REJECTED;
        String reason = allValid ? null : buildRejectionReason(checks);

        Order order = orderRepository.save(new Order(status, reason));
        for (LineCheck check : checks) {
            orderItemRepository.save(new OrderItem(order.getOrderId(), check.productId(), check.quantity()));
        }

        if (allValid) {
            for (LineCheck check : checks) {
                if (!inventoryService.reserve(check.productId(), check.quantity())) {
                    throw new IllegalStateException("Stock changed while reserving " + check.productId());
                }
            }
        }

        Map<String, LineCheck> checkByProductId = new LinkedHashMap<>();
        for (LineCheck check : checks) {
            checkByProductId.put(check.productId(), check);
        }
        String notReservedOutcome = canBackorder ? "BACKORDERED" : "NOT_RESERVED";
        List<OrderItemResultDto> itemResults = new ArrayList<>();
        for (OrderLineItemDto line : lineItems) {
            LineCheck check = checkByProductId.get(line.getProductId());
            String outcome = allValid ? "RESERVED"
                    : check.valid() ? notReservedOutcome : check.failureCode();
            itemResults.add(new OrderItemResultDto(line.getProductId(), outcome));
        }

        List<InventorySnapshotDto> inventorySnapshot = lineItems.stream()
                .map(li -> {
                    try {
                        return toSnapshot(inventoryService.getItem(li.getProductId()));
                    } catch (InventoryItemNotFoundException ex) {
                        return null;
                    }
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());

        if (allValid) {
            eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId()));
        } else if (!canBackorder) {
            eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));
        }
        // BACKORDERED publishes no event yet — BackorderResolutionListener
        // publishes OrderBackorderResolvedEvent later, when it's actually settled.

        return new OrderResponseDto(order.getOrderId(), status.name(), reason, itemResults, inventorySnapshot);
    }

    @Transactional
    public boolean cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(orderId);
        }

        boolean restocked = false;
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
            restocked = !items.isEmpty();
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        return restocked;
    }

    public List<OrderSummaryDto> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        List<OrderSummaryDto> summaries = new ArrayList<>();
        for (Order order : orders) {
            List<OrderLineSummaryDto> lines = orderItemRepository.findByOrderId(order.getOrderId()).stream()
                    .map(oi -> new OrderLineSummaryDto(oi.getProductId(), oi.getQuantity()))
                    .collect(Collectors.toList());
            summaries.add(new OrderSummaryDto(
                    order.getOrderId(), order.getStatus().name(), order.getReason(), order.getCreatedAt(), lines));
        }
        return summaries;
    }

    private String buildRejectionReason(List<LineCheck> checks) {
        return checks.stream()
                .filter(c -> !c.valid())
                .map(LineCheck::failureMessage)
                .collect(Collectors.joining("; "));
    }

    private InventorySnapshotDto toSnapshot(InventoryItem item) {
        return new InventorySnapshotDto(item.getProductId(), item.getName(), item.getStock());
    }
}
