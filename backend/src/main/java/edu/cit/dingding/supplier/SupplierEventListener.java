package edu.cit.dingding.supplier;

import edu.cit.dingding.inventory.events.LowStockEvent;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.stereotype.Component;

@Component
class SupplierEventListener {

    private static final int TARGET_STOCK_LEVEL = 20;

    private final SupplierGateway supplierGateway;

    SupplierEventListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        int unitsNeeded = TARGET_STOCK_LEVEL - event.getRemainingStock();
        if (unitsNeeded <= 0) {
            return;
        }
        supplierGateway.reorder(event.getProductId(), unitsNeeded);
    }
}
