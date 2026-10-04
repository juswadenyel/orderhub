package edu.cit.dingding.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Package-private on purpose: only classes inside edu.cit.dingding.inventory
 * (i.e. InventoryServiceImpl) should ever touch the inventory table directly.
 * Everything outside this package goes through InventoryService instead.
 */
interface InventoryRepository extends JpaRepository<InventoryItem, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<InventoryItem> findLockedByProductId(@Param("productId") String productId);
}
