package edu.cit.dingding.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private on purpose: only classes inside edu.cit.dingding.inventory
 * (i.e. InventoryServiceImpl) should ever touch the inventory table directly.
 * Everything outside this package goes through InventoryService instead.
 */
interface InventoryRepository extends JpaRepository<InventoryItem, String> {
}
