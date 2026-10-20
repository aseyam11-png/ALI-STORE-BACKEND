package com.store.fulfillment.service;

/**
 * The supplier may have accepted an order even though the caller did not receive
 * a definitive response. These failures must not trigger blind automatic replay.
 */
public class SupplierOutcomeUnknownException extends RuntimeException {

    public SupplierOutcomeUnknownException(String message) {
        super(message);
    }

    public SupplierOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
