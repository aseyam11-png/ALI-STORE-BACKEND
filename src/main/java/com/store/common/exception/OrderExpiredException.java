package com.store.common.exception;

public class OrderExpiredException extends ConflictException {
    public OrderExpiredException(String message) {
        super(message);
    }
}
