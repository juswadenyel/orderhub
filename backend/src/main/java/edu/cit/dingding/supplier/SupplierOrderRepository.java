package edu.cit.dingding.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    List<SupplierOrder> findByStatus(SupplierOrderStatus status);
    List<SupplierOrder> findByStatusIn(List<SupplierOrderStatus> statuses);
    List<SupplierOrder> findByProductIdAndStatusIn(String productId, List<SupplierOrderStatus> statuses);
}
