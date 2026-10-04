package edu.cit.dingding.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * One random instance ID generated when the app starts, held for as long
 * as it runs. Both Tiangge and LegacySupply calls send this in the
 * X-Client-Instance header (Tiangge's manual explicitly asks for it on
 * both, "so both systems can tell which running copy made them").
 *
 * Lives in config/, not in channel/ or supplier/, since it's infrastructure
 * both modules need — neither module "owns" the other's concept of it.
 */
@Component
public class InstanceIdentity {

    private final String id = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();

    public String getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long uptimeSeconds() {
        return java.time.Duration.between(startedAt, Instant.now()).getSeconds();
    }
}
