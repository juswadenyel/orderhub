package edu.cit.dingding.channel;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import edu.cit.dingding.config.InstanceIdentity;
import tools.jackson.databind.ObjectMapper;

/**
 * The only class that knows Tiangge speaks JSON over these specific paths.
 * Package-private, like everything else in this module except
 * SalesChannelGateway. Every call carries X-Client-Instance so Tiangge
 * (and, per its own manual, LegacySupply too — handled separately in the
 * supplier module) can tell which running copy of the app made it.
 */
@Component
class TiangeeClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 300;

    private final RestClient restClient;
    private final InstanceIdentity instanceIdentity;
    private final ObjectMapper objectMapper;

    TiangeeClient(RestClient.Builder builder,
                  @Value("${tiangge.base-url}") String baseUrl,
                  @Value("${tiangge.client-id}") String clientId,
                  @Value("${tiangge.api-key}") String apiKey,
                  InstanceIdentity instanceIdentity,
                  ObjectMapper objectMapper) {
        this.instanceIdentity = instanceIdentity;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Client-Id", clientId)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    // ---- wire DTOs (package-private, never leak outside this module) ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HeartbeatRequest(String appName, Instant startedAt, long uptimeSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListingDto(String sellerSku, String title, String supplierSku) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StockDto(String sellerSku, int available) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedLine(String sellerSku, int qty) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedEvent(long seq, String eventId, String type, String orderId,
                      Instant placedAt, Instant decisionDeadline,
                      Instant cancelledAt, Instant confirmDeadline,
                      List<FeedLine> lines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeedResponse(List<FeedEvent> events, long nextCursor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DecisionRequest(String decision, String shopOrderId, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResolutionRequest(String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CancellationConfirmRequest(boolean restocked) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(String error, String message) {
    }

    // ---- operations ----

    void heartbeat(String appName, Instant startedAt, long uptimeSeconds) {
        withRetry(() -> {
            restClient.post().uri("/instances/heartbeat")
                    .header("X-Client-Instance", instanceIdentity.getId())
                    .body(new HeartbeatRequest(appName, startedAt, uptimeSeconds))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    void publishListings(List<ListingDto> listings) {
        withRetry(() -> {
            restClient.put().uri("/listings")
                    .header("X-Client-Instance", instanceIdentity.getId())
                    .body(listings).retrieve().toBodilessEntity();
            return null;
        });
    }

    void publishStock(List<StockDto> stock) {
        withRetry(() -> {
            restClient.put().uri("/stock")
                    .header("X-Client-Instance", instanceIdentity.getId())
                    .body(stock).retrieve().toBodilessEntity();
            return null;
        });
    }

    FeedResponse getFeed(long after, int limit) {
        return withRetry(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/feed")
                        .queryParam("after", after)
                        .queryParam("limit", limit)
                        .build())
                .header("X-Client-Instance", instanceIdentity.getId())
                .retrieve()
                .body(FeedResponse.class));
    }

    /** Safe to call repeatedly with the same decision — Tiangge treats that as a no-op success. */
    void decide(String orderId, String decision, String shopOrderId, String reason) {
        post("/orders/" + orderId + "/decision", new DecisionRequest(decision, shopOrderId, reason));
    }

    /** 409 not_backordered is treated as "already resolved" — benign, not an error. */
    void resolve(String orderId, String status) {
        postIgnoring("/orders/" + orderId + "/resolution", new ResolutionRequest(status), "not_backordered");
    }

    /** 409 not_cancelled is treated as benign (log only, not retried as a failure). */
    void confirmCancellation(String orderId, boolean restocked) {
        postIgnoring("/orders/" + orderId + "/cancellation", new CancellationConfirmRequest(restocked), "not_cancelled");
    }

    // ---- plumbing ----

    private void post(String path, Object body) {
        withRetry(() -> {
            restClient.post().uri(path)
                    .header("X-Client-Instance", instanceIdentity.getId())
                    .body(body).retrieve().toBodilessEntity();
            return null;
        });
    }

    private void postIgnoring(String path, Object body, String benign409Code) {
        withRetry(() -> {
            try {
                restClient.post().uri(path)
                        .header("X-Client-Instance", instanceIdentity.getId())
                        .body(body).retrieve().toBodilessEntity();
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 409 && benign409Code.equals(errorCode(e))) {
                    return null; // already done — not a failure
                }
                throw classify(e);
            }
            return null;
        });
    }

    private <T> T withRetry(Supplier<T> action) {
        int attempt = 0;
        long backoff = BASE_BACKOFF_MS;
        while (true) {
            attempt++;
            try {
                return action.get();
            } catch (RestClientResponseException e) {
                RuntimeException classified = classify(e);
                if (classified instanceof ChannelPermanentException || attempt >= MAX_ATTEMPTS) {
                    throw classified;
                }
                sleep(backoff);
                backoff *= 2;
            } catch (ResourceAccessException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ChannelTransientException("Network error calling Tiangge", e);
                }
                sleep(backoff);
                backoff *= 2;
            }
        }
    }

    private static final Set<Integer> PERMANENT_HTTP_STATUSES = Set.of(400, 401, 404, 422);

    private RuntimeException classify(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String code = errorCode(e);
        if (PERMANENT_HTTP_STATUSES.contains(status)) {
            return new ChannelPermanentException("Tiangge rejected the request (" + code + "): " + e.getResponseBodyAsString());
        }
        // 409 (decision_conflict etc) and 503 (unavailable) — 409 here means a
        // GENUINE conflict (we already tried to decide differently), which a
        // retry won't fix either, but we still don't want a crash loop over
        // it, so treat it as permanent-but-logged rather than retried forever.
        if (status == 409) {
            System.err.println("[channel] Tiangge conflict (" + code + "): " + e.getResponseBodyAsString());
            return new ChannelPermanentException("Tiangge conflict (" + code + "): " + e.getResponseBodyAsString());
        }
        return new ChannelTransientException("Tiangge returned HTTP " + status + " (" + code + "): " + e.getResponseBodyAsString());
    }

    private String errorCode(RestClientResponseException e) {
        try {
            return objectMapper.readValue(e.getResponseBodyAsString(), ErrorBody.class).error();
        } catch (Exception parseFailure) {
            return "UNKNOWN";
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
