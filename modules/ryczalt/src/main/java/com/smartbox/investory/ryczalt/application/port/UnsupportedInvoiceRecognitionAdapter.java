package com.smartbox.investory.ryczalt.application.port;

import org.springframework.stereotype.Component;

/** Explicit native fallback until a document provider is configured. */
@Component
public class UnsupportedInvoiceRecognitionAdapter implements InvoiceRecognitionPort {
  @Override
  public RecognizedInvoice recognize(String filename, String contentType, byte[] content) {
    throw new IllegalArgumentException("No Ryczalt invoice recognition provider is configured");
  }
}
