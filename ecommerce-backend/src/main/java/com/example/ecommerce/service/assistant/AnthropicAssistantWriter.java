package com.example.ecommerce.service.assistant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.ecommerce.model.Product;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Optional: makes the assistant's reply sound more natural through the Anthropic Messages API. Off unless
 * app.ai.provider=anthropic and a key is set. It receives the shopper's message with emails and phone
 * numbers removed, the rule-based draft and the facts of the products already chosen; it cannot change
 * which products are shown. A reply that mentions a number not in those facts is thrown away.
 */
@Component
public class AnthropicAssistantWriter {
    static final String SYSTEM = """
            You are the friendly shopping assistant of an Indian saree shop. Rewrite the draft reply so it sounds warm
            and natural, in at most 55 words of plain text (no markdown, no lists). Mention only the products given.
            Never invent prices, discounts, policies, delivery times, materials or any other fact. Keep any prices exactly
            as given. If unsure, keep the draft's wording.
            """;
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s-]{7,}\\d");
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*");

    private final String provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper json;
    private final RestClient client;

    public AnthropicAssistantWriter(
            @Value("${app.ai.provider:none}") String provider,
            @Value("${app.ai.anthropic.api-key:}") String apiKey,
            @Value("${app.ai.anthropic.assistant-model:claude-haiku-5-5}") String model,
            @Value("${app.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            ObjectMapper json) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.json = json;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return "anthropic".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank();
    }

    /** The reworded reply, or null when the model's text failed the safety check. */
    public String reword(String shopperMessage, String draft, List<Product> shown) throws Exception {
        JsonNode reply = client.post()
                .uri(baseUrl + "/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .body(requestBody(shopperMessage, draft, shown))
                .retrieve()
                .body(JsonNode.class);
        String text = reply == null ? null : reply.path("content").path(0).path("text").asText(null);
        return accept(text, draft, shown);
    }

    Map<String, Object> requestBody(String shopperMessage, String draft, List<Product> shown) throws Exception {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (Product p : shown) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.getName());
            m.put("price", ShopAssistantService.money(ShopAssistantService.effectivePrice(p)));
            if (p.getFabric() != null) m.put("fabric", p.getFabric());
            if (p.getOccasion() != null) m.put("occasion", p.getOccasion());
            if (p.getColour() != null) m.put("colour", p.getColour());
            facts.add(m);
        }
        String message = PHONE.matcher(EMAIL.matcher(shopperMessage == null ? "" : shopperMessage).replaceAll("[email]")).replaceAll("[phone]");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 200);
        body.put("system", SYSTEM);
        body.put("messages", List.of(Map.of("role", "user", "content",
                "Shopper said: " + message + "\nDraft reply: " + draft + "\nProducts shown: " + json.writeValueAsString(facts))));
        return body;
    }

    /** Keep the model's text only if every number in it already appears in the draft or the product facts. */
    static String accept(String text, String draft, List<Product> shown) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty() || t.length() > 450) return null;
        Set<String> allowed = new HashSet<>(numbers(draft));
        for (Product p : shown) {
            allowed.addAll(numbers(ShopAssistantService.money(ShopAssistantService.effectivePrice(p))));
            allowed.addAll(numbers(p.getName()));
            if (p.getPrice() != null) allowed.addAll(numbers(ShopAssistantService.money(p.getPrice())));
        }
        for (String n : numbers(t)) if (!allowed.contains(n)) return null;
        return t;
    }

    private static List<String> numbers(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        Matcher m = NUMBER.matcher(s);
        while (m.find()) out.add(m.group().replace(",", ""));
        return out;
    }
}
