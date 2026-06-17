package com.example.demo.clients;

/**
 * Typed failure raised by {@link ExchangeRateClient} when the call to
 * exchangerate.host cannot be completed (network IO, non-2xx HTTP status,
 * malformed JSON, or interruption).
 *
 * <p>Mirrors {@code TwelveDataException} so both native HTTP wrappers expose
 * the same error-shape to callers.
 */
public class ExchangeRateException extends RuntimeException {

    public ExchangeRateException(String message) {
        super(message);
    }

    public ExchangeRateException(String message, Throwable cause) {
        super(message, cause);
    }
}

