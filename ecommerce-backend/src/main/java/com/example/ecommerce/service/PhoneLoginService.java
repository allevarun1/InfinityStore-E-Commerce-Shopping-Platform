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
import com.example.ecommerce.security.services.UserDetailsImpl;

/**
 * Signs a shopper in by a mobile number they proved with an OTP. The first sign-in creates a customer account
 * with no password; later sign-ins with the same number open the same account. Only the verified number is
 * matched, never the free-text phone on a profile, so nobody can claim an account by typing someone else's number.
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

    @Transactional
    public Result signIn(String verifiedPhone) {
        User existing = users.findByVerifiedPhone(verifiedPhone).orElse(null);
        if (existing != null) {
            return new Result(UserDetailsImpl.build(existing), false);
        }
        Role customer = roles.findByName(ERole.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Customer role is missing from the database"));
        User user = new User(freeUsername(verifiedPhone), null, encoder.encode(randomSecret()), null, null);
        user.setVerifiedPhone(verifiedPhone);
        user.setPhone(nationalNumber(verifiedPhone));
        user.setRoles(new java.util.HashSet<>(Set.of(customer)));
        User saved = users.save(user);
        audit.record("ACCOUNT_CREATED_BY_OTP", "User", saved.getId(), saved.getUsername());
        return new Result(UserDetailsImpl.build(saved), true);
    }

    /** 10-digit number for Indian mobiles (what checkout expects), the full international form otherwise. */
    static String nationalNumber(String e164) {
        return e164.startsWith("+91") && e164.length() == 13 ? e164.substring(3) : e164;
    }

    private String freeUsername(String e164) {
        String digits = e164.replaceAll("\\D", "");
        String base = "m" + digits.substring(Math.max(0, digits.length() - 12));
        String candidate = base;
        for (int i = 0; users.existsByUsername(candidate); i++) {
            candidate = base.substring(0, Math.min(base.length(), 16)) + (100 + RANDOM.nextInt(900)) + (i > 5 ? "x" : "");
        }
        return candidate;
    }

    /** Never shown or used: these accounts sign in by OTP. */
    private static String randomSecret() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
