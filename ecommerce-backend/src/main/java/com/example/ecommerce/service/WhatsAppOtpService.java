package com.example.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Login OTP sent on WhatsApp through Meta's WhatsApp Cloud API (an "authentication" template with a copy-code
 * button), far cheaper than SMS. Codes are random, kept only as a salted hash, valid for 5 minutes and 5 tries,
 * and each number may request at most one code per 30 seconds and five per hour. Off until the WhatsApp settings
 * are present.
 */
@Service
public class WhatsAppOtpService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9]\\d{9}$");
    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_GAP = Duration.ofSeconds(30);
    static final int MAX_SENDS_PER_HOUR = 5;
    static final int MAX_TRIES = 5;

    private final String token;
    private final String phoneNumberId;
    private final String template;
    private final String language;
    private final RestClient client;
    private final Map<String, Code> codes = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> sends = new ConcurrentHashMap<>();

    public WhatsAppOtpService(@Value("${app.whatsapp.token:}") String token,
                              @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                              @Value("${app.whatsapp.otp-template:login_otp}") String template,
                              @Value("${app.whatsapp.otp-language:en}") String language) {
        this.token = token == null ? "" : token.trim();
        this.phoneNumberId = phoneNumberId == null ? "" : phoneNumberId.trim();
        this.template = template == null || template.isBlank() ? "login_otp" : template.trim();
        this.language = language == null || language.isBlank() ? "en" : language.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().baseUrl("https://graph.facebook.com/v21.0").requestFactory(factory).build();
    }

    private record Code(String hash, String salt, Instant expiresAt, int tries) {
    }

    public boolean enabled() {
        return !token.isEmpty() && !phoneNumberId.isEmpty();
    }

    /** Sends a fresh code to a 10-digit Indian mobile number. */
    public void send(String mobile) {
        if (!enabled()) throw new BusinessException("WhatsApp OTP is not available yet");
        String number = normalise(mobile);
        throttle(number);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", "91" + number,
                "type", "template",
                "template", Map.of(
                        "name", template,
                        "language", Map.of("code", language),
                        "components", List.of(
                                Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", code))),
                                Map.of("type", "button", "sub_type", "url", "index", "0",
                                        "parameters", List.of(Map.of("type", "text", "text", code))))));
        try {
            client.post().uri("/{id}/messages", phoneNumberId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.error("WhatsApp OTP send failed: {}", e.getMessage());
            throw new BusinessException("We could not send the OTP on WhatsApp. Please check the number or try another way.");
        }
        remember(number, code);
    }

    /** True when the code is right; a used, expired or over-tried code never works again. */
    public boolean verify(String mobile, String code) {
        String number = normalise(mobile);
        Code c = codes.get(number);
        if (c == null || c.expiresAt().isBefore(Instant.now()) || c.tries() >= MAX_TRIES) {
            codes.remove(number);
            throw new BusinessException("This OTP has expired. Please request a new one.");
        }
        boolean ok = code != null && code.matches("\\d{6}")
                && MessageDigest.isEqual(c.hash().getBytes(StandardCharsets.UTF_8), hash(c.salt(), code).getBytes(StandardCharsets.UTF_8));
        if (ok) {
            codes.remove(number);
        } else {
            codes.put(number, new Code(c.hash(), c.salt(), c.expiresAt(), c.tries() + 1));
        }
        return ok;
    }

    public static String normalise(String mobile) {
        String digits = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (!INDIAN_MOBILE.matcher(digits).matches()) throw new BusinessException("Enter a valid 10-digit mobile number");
        return digits;
    }

    private void throttle(String number) {
        Instant now = Instant.now();
        Deque<Instant> recent = sends.computeIfAbsent(number, k -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) recent.pollFirst();
            if (!recent.isEmpty() && recent.peekLast().isAfter(now.minus(RESEND_GAP))) {
                throw new BusinessException("Please wait 30 seconds before asking for another OTP");
            }
            if (recent.size() >= MAX_SENDS_PER_HOUR) {
                throw new BusinessException("Too many OTP requests for this number. Please try again in an hour.");
            }
            recent.addLast(now);
        }
        if (sends.size() > 50_000) sends.entrySet().removeIf(e -> e.getValue().isEmpty());
        if (codes.size() > 50_000) codes.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    void remember(String number, String code) {
        byte[] s = new byte[8];
        RANDOM.nextBytes(s);
        String salt = HexFormat.of().formatHex(s);
        codes.put(number, new Code(hash(salt, code), salt, Instant.now().plus(CODE_TTL), 0));
    }

    private static String hash(String salt, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
