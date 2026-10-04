package edu.cit.dingding.supplier;

import org.springframework.stereotype.Component;

// Must be a Spring bean (singleton) so the SAME cached token is shared by
// every call LegacySupplyClient makes — a plain `new LegacySupplySession()`
// injected manually would never get wired up by Spring at all.
@Component
class LegacySupplySession {
    private volatile String token;

    String getCachedToken() { return token; }
    void setToken(String token) { this.token = token; }
    void invalidate() { this.token = null; }
    boolean hasToken() { return token != null; }
}
