package edu.cit.dingding.supplier;

import edu.cit.dingding.config.InstanceIdentity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The only class that knows LegacySupply speaks XML, uses SupplierSku/Qty/
 * Uom, and has session tokens. No XML/HTTP library dependency was added to
 * pom.xml — java.net.http and the JDK's built-in org.w3c.dom parser cover
 * this fixed, small XML shape without pulling in JAXB.
 */
@Component
class LegacySupplyClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 300;
    private static final Set<String> SESSION_ERROR_CODES = Set.of("E-AUTH-02", "E-AUTH-03", "E-AUTH-07");

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final HttpClient httpClient;
    private final LegacySupplySession session;
    private final InstanceIdentity instanceIdentity;

    LegacySupplyClient(
            @Value("${legacysupply.base-url}") String baseUrl,
            @Value("${legacysupply.client-id}") String clientId,
            @Value("${legacysupply.api-key}") String apiKey,
            LegacySupplySession session,
            InstanceIdentity instanceIdentity) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.session = session;
        this.instanceIdentity = instanceIdentity;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    record PlacedOrder(String poNumber, int statusCode, String uom) {
    }

    record TrackedOrder(String poNumber, int statusCode) {
    }

    PlacedOrder placeOrder(String sku, int qty, String buyerRef, String requestId) {
        String body = "<PurchaseOrder>"
                + "<SupplierSku>" + xmlEscape(sku) + "</SupplierSku>"
                + "<Qty>" + qty + "</Qty>"
                + "<BuyerRef>" + xmlEscape(buyerRef) + "</BuyerRef>"
                + "</PurchaseOrder>";
        String response = withRetry(() -> executeAuthenticated("POST", "/purchase-orders", body, requestId));
        Document doc = parse(response);
        return new PlacedOrder(text(doc, "PoNumber"), Integer.parseInt(text(doc, "StatusCode")), text(doc, "Uom"));
    }

    TrackedOrder getStatus(String poNumber) {
        String response = withRetry(() ->
                executeAuthenticated("GET", "/purchase-orders/" + urlEncode(poNumber), null, null));
        Document doc = parse(response);
        return new TrackedOrder(text(doc, "PoNumber"), Integer.parseInt(text(doc, "StatusCode")));
    }

    List<TrackedOrder> findByBuyerRef(String buyerRef) {
        String response = withRetry(() ->
                executeAuthenticated("GET", "/purchase-orders?buyerRef=" + urlEncode(buyerRef), null, null));
        Document doc = parse(response);
        NodeList nodes = doc.getElementsByTagName("PurchaseOrderStatus");
        if (nodes.getLength() == 0) {
            nodes = doc.getElementsByTagName("Item");
        }
        List<TrackedOrder> results = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String po = firstChildText(el, "PoNumber");
            String status = firstChildText(el, "StatusCode");
            if (po != null && status != null) {
                results.add(new TrackedOrder(po, Integer.parseInt(status)));
            }
        }
        return results;
    }

    private String withRetry(java.util.function.Supplier<String> singleAttempt) {
        int attempt = 0;
        long backoff = BASE_BACKOFF_MS;
        while (true) {
            attempt++;
            try {
                return singleAttempt.get();
            } catch (SupplierPermanentException e) {
                throw e;
            } catch (SupplierTransientException e) {
                if (attempt >= MAX_ATTEMPTS) throw e;
                sleep(backoff);
                backoff *= 3;
            }
        }
    }

    private String executeAuthenticated(String method, String path, String body, String requestId) {
        if (!session.hasToken()) {
            authenticate();
        }
        HttpResponse<String> response = send(method, path, body, requestId, session.getCachedToken());

        if (response.statusCode() == 401) {
            String code = errorCode(response.body());
            if (SESSION_ERROR_CODES.contains(code)) {
                session.invalidate();
                authenticate();
                response = send(method, path, body, requestId, session.getCachedToken());
            }
        }
        return handleResponse(response);
    }

    private void authenticate() {
        String body = "<AuthRequest>"
                + "<ClientId>" + xmlEscape(clientId) + "</ClientId>"
                + "<ApiKey>" + xmlEscape(apiKey) + "</ApiKey>"
                + "</AuthRequest>";
        HttpResponse<String> response = send("POST", "/auth/token", body, null, null);
        Document doc = parse(handleResponse(response));
        session.setToken(text(doc, "SessionToken"));
    }

    private HttpResponse<String> send(String method, String path, String body, String requestId, String sessionToken) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Client-Instance", instanceIdentity.getId());

            if (sessionToken != null) {
                builder.header("X-LS-Session", sessionToken);
            }
            if (requestId != null) {
                builder.header("X-Request-Id", requestId);
            }
            if (body != null) {
                builder.header("Content-Type", "application/xml")
                        .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SupplierTransientException("Network error calling LegacySupply " + method + " " + path, e);
        }
    }

    private String handleResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        String code = errorCode(response.body());
        if (Set.of("E-SKU-02", "E-QTY-11", "E-REF-05", "E-FMT-01", "E-FMT-02", "E-AUTH-01", "E-QRY-06", "E-PO-04")
                .contains(code)) {
            throw new SupplierPermanentException("LegacySupply rejected the request (" + code + "): " + response.body());
        }
        throw new SupplierTransientException("LegacySupply returned HTTP " + status + " (" + code + "): " + response.body());
    }

    private String errorCode(String responseBody) {
        try {
            return text(parse(responseBody), "Code");
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new SupplierTransientException("Could not parse LegacySupply's XML response", e);
        }
    }

    private static String text(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            throw new SupplierTransientException("Expected element <" + tagName + "> missing from LegacySupply response");
        }
        return nodes.item(0).getTextContent();
    }

    private static String firstChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
    }

    private static String xmlEscape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
