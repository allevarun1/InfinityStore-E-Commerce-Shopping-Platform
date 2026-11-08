package com.example.ecommerce.controller;

import com.example.ecommerce.dto.*;
import com.example.ecommerce.model.ERole;
import com.example.ecommerce.model.Role;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.RoleRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.FirebasePhoneVerifier;
import com.example.ecommerce.security.GoogleIdTokenVerifier;
import com.example.ecommerce.security.jwt.JwtUtils;
import com.example.ecommerce.security.services.UserDetailsImpl;
import com.example.ecommerce.service.LoginAttemptService;
import com.example.ecommerce.service.PasswordResetService;
import com.example.ecommerce.service.PhoneLoginService;
import com.example.ecommerce.service.TruecallerLoginService;
import com.example.ecommerce.service.WhatsAppOtpService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String RESET_REQUESTED = "If an account exists for that email, a password reset link has been sent.";

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    PasswordResetService passwordResetService;

    @Autowired
    LoginAttemptService loginAttempts;

    @Autowired
    FirebasePhoneVerifier phoneVerifier;

    @Autowired
    PhoneLoginService phoneLogins;

    public record PhoneLoginRequest(@NotBlank @Size(max = 4096) String idToken) {
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        loginAttempts.checkAllowed(loginRequest.getUsername());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
        } catch (AuthenticationException e) {
            loginAttempts.recordFailure(loginRequest.getUsername());
            throw e;
        }
        loginAttempts.recordSuccess(loginRequest.getUsername());

        // Shoppers stay signed in for 30 days and the owner for 14, renewed every time they visit (see /refresh).
        String jwt = jwtUtils.generateJwtToken(authentication, sessionMs(authentication.getAuthorities()));

        return ResponseEntity.ok(jwtResponse(jwt, (UserDetailsImpl) authentication.getPrincipal()));
    }

    /**
     * Sign in (or sign up) with a mobile number. The storefront sends the OTP through Firebase and posts the
     * resulting ID token here; the account is found or created by the verified number.
     */
    @PostMapping("/phone")
    public ResponseEntity<JwtResponse> phoneLogin(@Valid @RequestBody PhoneLoginRequest request) {
        return ResponseEntity.ok(customerSession(phoneLogins.signIn(phoneVerifier.verify(request.idToken())).user()));
    }

    /** Customers who sign in stay signed in for 30 days; the admin console for 14. Each visit renews the clock. */
    static final long CUSTOMER_SESSION_MS = 30L * 24 * 60 * 60 * 1000;
    static final long ADMIN_SESSION_MS = 14L * 24 * 60 * 60 * 1000;

    private static long sessionMs(java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> roles) {
        boolean admin = roles.stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? ADMIN_SESSION_MS : CUSTOMER_SESSION_MS;
    }

    /**
     * Sliding session: a signed-in browser swaps its still-valid token for a fresh one, so someone who visits at least
     * once inside the session length is never asked to sign in again.
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl details)) {
            return ResponseEntity.status(401).build();
        }
        String jwt = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()), sessionMs(details.getAuthorities()));
        return ResponseEntity.ok(jwtResponse(jwt, details));
    }

    @Autowired
    GoogleIdTokenVerifier googleVerifier;

    @Autowired
    TruecallerLoginService truecaller;

    @Autowired
    WhatsAppOtpService whatsappOtp;

    public record GoogleLoginRequest(@NotBlank @Size(max = 4096) String credential) {
    }

    public record OtpSendRequest(@NotBlank @Size(max = 16) String phone) {
    }

    public record OtpVerifyRequest(@NotBlank @Size(max = 16) String phone, @NotBlank @Size(max = 6) String code) {
    }

    public record TruecallerStart(String nonce) {
    }

    /** status is PENDING, REJECTED or VERIFIED; session is set only when VERIFIED. */
    public record TruecallerStatus(String status, JwtResponse session) {
    }

    private JwtResponse customerSession(UserDetailsImpl details) {
        String jwt = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()), sessionMs(details.getAuthorities()));
        return jwtResponse(jwt, details);
    }

    /** Continue with Google: the storefront posts the ID token Google Identity Services returned. */
    @PostMapping("/google")
    public ResponseEntity<JwtResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(customerSession(phoneLogins.signIn(googleVerifier.verify(request.credential())).user()));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        whatsappOtp.send(request.phone());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<JwtResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        if (!whatsappOtp.verify(request.phone(), request.code())) {
            throw new com.example.ecommerce.service.BusinessException("That OTP is not right. Please check WhatsApp and try again.");
        }
        String e164 = "+91" + WhatsAppOtpService.normalise(request.phone());
        return ResponseEntity.ok(customerSession(phoneLogins.signIn(e164).user()));
    }

    @PostMapping("/truecaller/start")
    public ResponseEntity<TruecallerStart> truecallerStart() {
        return ResponseEntity.ok(new TruecallerStart(truecaller.start()));
    }

    /** Called by Truecaller's servers, not by browsers. Always 200 so Truecaller does not retry forged calls. */
    @PostMapping("/truecaller/callback")
    public ResponseEntity<Void> truecallerCallback(@RequestBody(required = false) JsonNode body) {
        if (body != null) truecaller.callback(body);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/truecaller/status/{nonce}")
    public ResponseEntity<TruecallerStatus> truecallerStatus(@PathVariable String nonce) {
        var result = truecaller.poll(nonce);
        if (result.getKey() != TruecallerLoginService.State.VERIFIED) {
            return ResponseEntity.ok(new TruecallerStatus(result.getKey().name(), null));
        }
        var v = result.getValue();
        UserDetailsImpl details = phoneLogins.signIn(v.phone(),
                new PhoneLoginService.Profile(v.firstName(), v.lastName(), v.email())).user();
        return ResponseEntity.ok(new TruecallerStatus("VERIFIED", customerSession(details)));
    }

    private static JwtResponse jwtResponse(String jwt, UserDetailsImpl userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());
        return new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getFirstName(),
                userDetails.getLastName(),
                roles);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Username is already taken"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email is already in use"));
        }

        User user = new User(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()),
                signUpRequest.getFirstName(),
                signUpRequest.getLastName());

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName(ERole.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Customer role is missing from the database"));
        roles.add(userRole);

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.request(request.getEmail());
        return ResponseEntity.ok(new MessageResponse(RESET_REQUESTED));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.getEmail(), request.getCode(), request.getNewPassword(),
                request.getConfirmPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
    }
}
