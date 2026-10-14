package com.example.ecommerce.service;

import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Password recovery with a 256-bit random token that is emailed as a link, stored only as a
 * SHA-256 hash, expires, and is invalidated after too many wrong guesses.
 */
@Service
public class PasswordResetService {
    static final String INVALID = "This reset link is invalid or has expired";

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private MailService mailService;

    @Autowired
    private AuditService auditService;

    @Value("${app.reset.token-ttl-minutes:30}")
    private long ttlMinutes;

    @Value("${app.reset.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Value("${app.cors.allowed-origins:}")
    private String corsOrigins;

    private String baseUrl() {
        if (!frontendUrl.isBlank()) {
            return frontendUrl;
        }
        // Fall back to the first configured storefront origin
        return corsOrigins.split(",")[0].trim();
    }

    /** Always completes silently so callers cannot learn whether an email is registered. */
    @Transactional
    public void request(String email) {
        userRepository.findByEmail(email.trim()).ifPresent(user -> {
            byte[] raw = new byte[32];
            random.nextBytes(raw);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            user.setResetTokenHash(hash(token));
            user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
            user.setResetAttempts(0);
            user.setResetToken(null);
            userRepository.save(user);
            String link = baseUrl().replaceAll("/+$", "") + "/reset-password?email="
                    + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8) + "&token=" + token;
            mailService.sendPasswordReset(user.getEmail(), link, ttlMinutes);
        });
    }

    /** Failed guesses must persist (attempt counter), so business errors do not roll back. */
    @Transactional(noRollbackFor = BusinessException.class)
    public void reset(String email, String token, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("New password and confirm password do not match");
        }
        User user = userRepository.findByEmail(email.trim()).orElseThrow(() -> new BusinessException(INVALID));
        String stored = user.getResetTokenHash();
        LocalDateTime expires = user.getResetTokenExpiresAt();
        if (stored == null || expires == null || expires.isBefore(LocalDateTime.now())
                || user.getResetAttempts() >= maxAttempts) {
            clearToken(user);
            throw new BusinessException(INVALID);
        }
        boolean match = MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),
                hash(token.trim()).getBytes(StandardCharsets.UTF_8));
        if (!match) {
            user.setResetAttempts(user.getResetAttempts() + 1);
            if (user.getResetAttempts() >= maxAttempts) {
                user.setResetTokenHash(null);
                user.setResetTokenExpiresAt(null);
            }
            userRepository.save(user);
            throw new BusinessException(INVALID);
        }
        user.setPassword(encoder.encode(newPassword));
        clearToken(user);
        auditService.record("PASSWORD_RESET", "USER", user.getId(), "completed via emailed token");
    }

    private void clearToken(User user) {
        user.setResetTokenHash(null);
        user.setResetTokenExpiresAt(null);
        user.setResetAttempts(0);
        user.setResetToken(null);
        userRepository.save(user);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
