package edu.cit.dingding.notification;

import java.util.List;

public interface NotificationService {
    void log(String message);
    List<Notification> getAll();
}
