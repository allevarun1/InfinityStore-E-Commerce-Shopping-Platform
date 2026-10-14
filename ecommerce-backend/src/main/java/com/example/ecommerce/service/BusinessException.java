package com.example.ecommerce.service;

/** A failure whose message is safe to show to the caller (HTTP 400). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
