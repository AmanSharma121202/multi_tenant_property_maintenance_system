package com.housing.billing.messaging;

public class DelayNotElapsedException extends RuntimeException {
    public DelayNotElapsedException(String message) {
        super(message);
    }
}

