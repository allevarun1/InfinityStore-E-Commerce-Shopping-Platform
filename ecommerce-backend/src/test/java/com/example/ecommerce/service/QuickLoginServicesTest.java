package com.example.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** WhatsApp OTP rules and Truecaller callback safety, without calling Meta or Truecaller. */
class QuickLoginServicesTest {
    private final WhatsAppOtpService otp = new WhatsAppOtpService("token", "12345", "login_otp", "en");

    @Test
    void otpWorksOnceAndOnlyForTheRightNumber() {
        otp.remember("9876543210", "123456");
        assertFalse(otp.verify("9876543210", "000000"));
        assertTrue(otp.verify("+91 98765 43210", "123456"), "the number may be typed with +91 and spaces");
        assertThrows(BusinessException.class, () -> otp.verify("9876543210", "123456"), "a used code is gone");
    }

    @Test
    void otpLocksAfterFiveWrongTries() {
        otp.remember("9876500000", "654321");
        for (int i = 0; i < WhatsAppOtpService.MAX_TRIES; i++) assertFalse(otp.verify("9876500000", "111111"));
        assertThrows(BusinessException.class, () -> otp.verify("9876500000", "654321"), "even the right code is refused now");
    }

    @Test
    void onlyIndianMobileNumbersAreAccepted() {
        assertEquals("9876543210", WhatsAppOtpService.normalise("919876543210"));
        assertThrows(BusinessException.class, () -> WhatsAppOtpService.normalise("12345"));
        assertThrows(BusinessException.class, () -> WhatsAppOtpService.normalise("5876543210"));
        assertThrows(BusinessException.class, () -> otp.verify("9876543211", "123456"), "no code was sent to this number");
    }

    @Test
    void whatsappIsOffWithoutItsSettings() {
        WhatsAppOtpService off = new WhatsAppOtpService("", "", "", "");
        assertFalse(off.enabled());
        assertThrows(BusinessException.class, () -> off.send("9876543210"));
    }

    @Test
    void truecallerOnlyTrustsItsOwnProfileServers() {
        assertTrue(TruecallerLoginService.trustedEndpoint("https://profile4-noneu.truecaller.com/v1/default"));
        assertFalse(TruecallerLoginService.trustedEndpoint("http://profile4-noneu.truecaller.com/v1/default"));
        assertFalse(TruecallerLoginService.trustedEndpoint("https://truecaller.com.evil.example/v1"));
        assertFalse(TruecallerLoginService.trustedEndpoint("https://evil.example/?x=.truecaller.com"));
        assertFalse(TruecallerLoginService.trustedEndpoint("https://user@api.truecaller.com/v1"));
        assertFalse(TruecallerLoginService.trustedEndpoint("https://api.truecaller.com:8443/v1"));
        assertFalse(TruecallerLoginService.trustedEndpoint("not a url"));
    }

    @Test
    void truecallerRequestsAreSingleUseAndRejectionsAreReported() throws Exception {
        TruecallerLoginService tc = new TruecallerLoginService("key", "Shop");
        ObjectMapper json = new ObjectMapper();
        String nonce = tc.start();
        assertEquals(TruecallerLoginService.State.PENDING, tc.poll(nonce).getKey());

        // A forged callback pointing elsewhere is ignored, and unknown request IDs do nothing.
        tc.callback(json.readTree("{\"requestId\":\"" + nonce + "\",\"accessToken\":\"t\",\"endpoint\":\"https://evil.example/p\"}"));
        tc.callback(json.readTree("{\"requestId\":\"unknown\",\"status\":\"user_rejected\"}"));
        assertEquals(TruecallerLoginService.State.PENDING, tc.poll(nonce).getKey());

        tc.callback(json.readTree("{\"requestId\":\"" + nonce + "\",\"status\":\"user_rejected\"}"));
        assertEquals(TruecallerLoginService.State.REJECTED, tc.poll(nonce).getKey());
        assertThrows(BusinessException.class, () -> tc.poll(nonce), "a finished request cannot be read twice");
        assertThrows(BusinessException.class, () -> new TruecallerLoginService("", "").start());
    }
}
