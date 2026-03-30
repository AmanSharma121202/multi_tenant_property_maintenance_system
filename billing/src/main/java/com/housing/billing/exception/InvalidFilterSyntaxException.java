package com.housing.billing.exception;

public class InvalidFilterSyntaxException extends RuntimeException {
    public InvalidFilterSyntaxException(String message) {
        super("unexpected token");
    }
}

