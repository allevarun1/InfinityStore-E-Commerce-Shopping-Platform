package com.example.ecommerce.security.services;

import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    UserRepository userRepository;

    /**
     * Every signed-in request looks the user up, and the database is a long round trip away, so the answer is kept for
     * a short while. A role change, deletion or password reset therefore takes effect within TTL_MS.
     */
    private static final long TTL_MS = 30_000;
    private static final int MAX_ENTRIES = 5_000;

    private record Cached(UserDetails details, long expiresAt) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        long now = System.currentTimeMillis();
        Cached hit = cache.get(username);
        if (hit != null && hit.expiresAt() > now) return hit.details();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

        UserDetails details = UserDetailsImpl.build(user);
        if (cache.size() >= MAX_ENTRIES) cache.clear();
        cache.put(username, new Cached(details, now + TTL_MS));
        return details;
    }

    /** Forget a user at once, for example after their password changes. */
    public void evict(String username) {
        cache.remove(username);
    }
}
