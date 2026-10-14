package com.example.ecommerce.service.studio;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Optional writer backed by the Anthropic Messages API. Off unless app.ai.provider=anthropic and an API
 * key is set. It sends only the facts the owner typed into Product Studio, never customer or order data.
 * Not verified against the live API in this repository: the request shape is covered by a unit test only.
 */
@Component
public class AnthropicListingWriter implements ListingWriter {
    static final String SYSTEM = """
            You write product listings for an Indian saree shop. Use ONLY the facts supplied by the owner.
            Do not invent or imply materials, purity, origin, certifications, handwork, history or any other claim
            that is not in the facts. Do not mention price. Plain, warm, specific language with no hype.
            Reply with a single JSON object and nothing else: {"name": "...", "description": "..."}.
            The name is at most 80 characters. The description is 40 to 110 words.
            """;

    private final String provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper json;
    private final RestClient client;

    public AnthropicListingWriter(
            @Value("${app.ai.provider:none}") String provider,
            @Value("${app.ai.anthropic.api-key:}") String apiKey,
            @Value("${app.ai.anthropic.model:claude-sonnet-5-5}") String model,
            @Value("${app.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            ObjectMapper json) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.json = json;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return "anthropic".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
    }

    @Override public String id() { return "anthropic"; }

    @Override public boolean external() { return true; }

    /** The JSON body sent to the API. Exposed for tests so the exact payload can be inspected. */
    Map<String, Object> requestBody(String categoryName, StudioRequest f) throws Exception {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("category", categoryName);
        put(facts, "fabric", f.getFabric());
        put(facts, "weave", f.getWeave());
        put(facts, "occasion", f.getOccasion());
        put(facts, "colour", f.getColour());
        put(facts, "region", f.getRegion());
        if (f.getLengthMeters() != null) facts.put("lengthMeters", f.getLengthMeters());
        if (f.getBlouseIncluded() != null) facts.put("blouseIncluded", f.getBlouseIncluded());
        put(facts, "careInstructions", f.getCareInstructions());
        put(facts, "ownerNotes", f.getNotes());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 600);
        body.put("system", SYSTEM);
        body.put("messages", java.util.List.of(Map.of("role", "user", "content", "Facts:\n" + json.writeValueAsString(facts))));
        return body;
    }

    @Override
    public ListingText write(String categoryName, StudioRequest facts) throws Exception {
        if (!configured()) throw new IllegalStateException("Anthropic writer is not configured");
        JsonNode reply = client.post()
                .uri(baseUrl + "/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .body(requestBody(categoryName, facts))
                .retrieve()
                .body(JsonNode.class);
        return parse(reply);
    }

    ListingText parse(JsonNode reply) throws Exception {
        String text = reply == null ? null : reply.path("content").path(0).path("text").asText(null);
        if (text == null || text.isBlank()) throw new IllegalStateException("Empty reply from the AI provider");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalStateException("The AI reply was not JSON");
        JsonNode out = json.readTree(text.substring(start, end + 1));
        String name = out.path("name").asText("").trim();
        String description = out.path("description").asText("").trim();
        if (name.isEmpty() || description.isEmpty()) throw new IllegalStateException("The AI reply was missing a name or description");
        return new ListingText(name.length() > 100 ? name.substring(0, 100) : name,
                description.length() > 2000 ? description.substring(0, 2000) : description);
    }

    private static void put(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v.trim());
    }
}
