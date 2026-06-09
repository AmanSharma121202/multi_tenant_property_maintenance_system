package com.housing.billing.exception;

public class TenantInactiveException extends RuntimeException {
    public TenantInactiveException(String message) {
        super(message);
    }
}
