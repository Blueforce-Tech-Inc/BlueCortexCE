package com.ablueforce.cortexce.common;

import java.io.IOException;

/**
 * Raised while reading a request body that exceeded the configured limit.
 *
 * <p>{@link IOException} so it can be thrown from the request input stream without any
 * caller in the parsing stack being able to swallow it silently. The global exception
 * handler turns it into {@code 413 payload_too_large} instead of the usual
 * {@code 400 invalid_request} for unreadable bodies.
 */
public class PayloadTooLargeException extends IOException {

    private final long limitBytes;

    public PayloadTooLargeException(long limitBytes) {
        super("Request body exceeds " + limitBytes + " bytes");
        this.limitBytes = limitBytes;
    }

    public long getLimitBytes() {
        return limitBytes;
    }
}
