package edu.cit.dingding.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "message")
    private String message;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Notification() {
        // required by JPA
    }

    public Notification(String message) {
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
