package edu.cit.dingding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling turns on every @Scheduled job across all modules:
// heartbeats, feed polling, supplier retry/tracking. Without this line
// they're silently never called — no error, no warning.
@EnableScheduling
@SpringBootApplication
public class OrderHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderHubApplication.class, args);
    }
}
