package com.smartbox.investory.longterm.api.model;

/** Public Long-Term API model. */
public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
