package com.example.ecommerce.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.model.ERole;
import com.example.ecommerce.model.Role;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.RoleRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.GoogleIdTokenVerifier;
import com.example.ecommerce.security.services.UserDetailsImpl;

/**
 * Passwordless customer sign-in: a mobile number the shopper proved (WhatsApp OTP, Truecaller or Firebase OTP) or a
 * Google account. The first sign-in creates a customer account with no usable password; later sign-ins open the same
 * account. Only the verified number or Google ID is matched, never the free-text phone on a profile, so nobody can
 * claim an account by typing someone else's number. Admin accounts can never be opened this way.
 */
@Service
public class PhoneLoginService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private AuditService audit;

    public record Result(UserDetailsImpl user, boolean created) {
    }

    /** Name and email a verifier vouches for along with the number (Truecaller gives them); any part may be null. */
    public record Profile(String firstName, String lastName, String email) {
        public static final Profile NONE = new Profile(null, null, null);
    }

    @Transactional
    public Result signIn(String verifiedPhone) {
        return signIn(verifiedPhone, Profile.NONE);
    }

    @Transactional
    public Result signIn(String verifiedPhone, Profile profile) {
        User existing = users.findByVerifiedPhone(verifiedPhone).orElse(null);
        if (existing != null) {
            refuseAdmin(existing);
            if (fillBlanks(existing, profile)) users.save(existing);
            return new Result(UserDetailsImpl.build(existing), false);
        }
        User user = newCustomer(freeUsername("m" + digitsTail(verifiedPhone)));
        user.setVerifiedPhone(verifiedPhone);
        user.setPhone(nationalNumber(verifiedPhone));
        fillBlanks(user, profile);
        User saved = users.save(user);
        audit.record("ACCOUNT_CREATED_BY_OTP", "User", saved.getId(), saved.getUsername());
        return new Result(UserDetailsImpl.build(saved), true);
    }

    /**
     * Google sign-in. Matched by Google's account ID; a customer who registered earlier with the same (Google-verified)
     * email is linked to it, so their orders and addresses stay together.
     */
    @Transactional
    public Result signIn(GoogleIdTokenVerifier.GoogleUser google) {
        User existing = users.findByGoogleSubject(google.subject()).orElse(null);
        if (existing == null) {
            existing = users.findByEmail(google.email()).orElse(null);
            if (existing != null) {
                refuseAdmin(existing);
                existing.setGoogleSubject(google.subject());
                audit.record("GOOGLE_LINKED", "User", existing.getId(), existing.getUsername());
            }
        }
        if (existing != null) {
            refuseAdmin(existing);
            fillBlanks(existing, new Profile(google.firstName(), google.lastName(), null));
            users.save(existing);
            return new Result(UserDetailsImpl.build(existing), false);
        }
        String local = google.email().substring(0, google.email().indexOf('@')).replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        User user = newCustomer(freeUsername("g" + (local.isEmpty() ? "user" : local)));
        user.setGoogleSubject(google.subject());
        fillBlanks(user, new Profile(google.firstName(), google.lastName(), google.email()));
        User saved = users.save(user);
        audit.record("ACCOUNT_CREATED_BY_GOOGLE", "User", saved.getId(), saved.getUsername());
        return new Result(UserDetailsImpl.build(saved), true);
    }

    private User newCustomer(String username) {
        Role customer = roles.findByName(ERole.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Customer role is missing from the database"));
        User user = new User(username, null, encoder.encode(randomSecret()), null, null);
        user.setRoles(new java.util.HashSet<>(Set.of(customer)));
        return user;
    }

    private static void refuseAdmin(User user) {
        if (user.getRoles().stream().anyMatch(r -> r.getName() == ERole.ROLE_ADMIN)) {
            throw new BusinessException("This account signs in with its username and password");
        }
    }

    /** Fills only what is still empty; never overwrites what the customer typed. Returns true when something changed. */
    private boolean fillBlanks(User user, Profile p) {
        boolean changed = false;
        if (blank(user.getFirstName()) && !blank(p.firstName())) { user.setFirstName(clip(p.firstName(), 100)); changed = true; }
        if (blank(user.getLastName()) && !blank(p.lastName())) { user.setLastName(clip(p.lastName(), 100)); changed = true; }
        String email = p.email() == null ? null : p.email().trim().toLowerCase();
        if (blank(user.getEmail()) && !blank(email) && email.length() <= 50 && !users.existsByEmail(email)) {
            user.setEmail(email);
            changed = true;
        }
        return changed;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String clip(String s, int max) {
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** 10-digit number for Indian mobiles (what checkout expects), the full international form otherwise. */
    static String nationalNumber(String e164) {
        return e164.startsWith("+91") && e164.length() == 13 ? e164.substring(3) : e164;
    }

    private static String digitsTail(String e164) {
        String digits = e164.replaceAll("\\D", "");
        return digits.substring(Math.max(0, digits.length() - 12));
    }

    private String freeUsername(String base) {
        String trimmed = base.length() > 16 ? base.substring(0, 16) : base;
        String candidate = trimmed;
        for (int i = 0; users.existsByUsername(candidate); i++) {
            candidate = trimmed + (100 + RANDOM.nextInt(900)) + (i > 5 ? "x" : "");
        }
        return candidate;
    }

    /** Never shown or used: these accounts sign in without a password. */
    private static String randomSecret() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
