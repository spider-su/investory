package com.smartbox.investory.ryczalt.application.port;

/** Explicit native fallback until a document provider is configured. */
@Deprecated(forRemoval = true)
public class UnsupportedInvoiceRecognitionAdapter implements InvoiceRecognitionPort {
  @Override
  public RecognizedInvoice recognize(String filename, String contentType, byte[] content) {
    throw new IllegalArgumentException("No Ryczalt invoice recognition provider is configured");
  }
}
