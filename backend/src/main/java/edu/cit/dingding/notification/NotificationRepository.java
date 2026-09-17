package edu.cit.dingding.notification;

import org.springframework.data.jpa.repository.JpaRepository;

// Package-private, same reasoning as InventoryRepository — nothing outside
// this package needs to touch the notifications table directly.
interface NotificationRepository extends JpaRepository<Notification, Long> {
}
