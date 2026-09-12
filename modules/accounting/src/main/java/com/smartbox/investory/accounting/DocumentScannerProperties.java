package com.smartbox.investory.accounting;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Simple feature flags for the draft document scanner pipeline. */
@Component
@ConfigurationProperties(prefix = "app.accounting.document-scanner")
class DocumentScannerProperties {
  private boolean pdfEnabled = true;
  private boolean imageEnabled = true;
  private boolean aiFallbackEnabled = true;
  private long maxBytes = 12L * 1024L * 1024L;
  private int maxPages = 50;

  public boolean isPdfEnabled() {
    return pdfEnabled;
  }

  public void setPdfEnabled(boolean pdfEnabled) {
    this.pdfEnabled = pdfEnabled;
  }

  public boolean isImageEnabled() {
    return imageEnabled;
  }

  public void setImageEnabled(boolean imageEnabled) {
    this.imageEnabled = imageEnabled;
  }

  public boolean isAiFallbackEnabled() {
    return aiFallbackEnabled;
  }

  public void setAiFallbackEnabled(boolean aiFallbackEnabled) {
    this.aiFallbackEnabled = aiFallbackEnabled;
  }

  public long getMaxBytes() {
    return maxBytes;
  }

  public void setMaxBytes(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  public int getMaxPages() {
    return maxPages;
  }

  public void setMaxPages(int maxPages) {
    this.maxPages = maxPages;
  }
}
