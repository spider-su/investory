package com.example.demo.services;

/**
 * Signals an unrecoverable failure when calling the TwelveData REST API.
 * Used internally by {@link TwelveDataService} so the public methods can either rethrow
 * as a {@link RuntimeException} for the chunked stock sync or swallow + log for the
 * benchmark cache (whose callers fall back to cached values on any failure).
 */
class TwelveDataException extends Exception {

    TwelveDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

