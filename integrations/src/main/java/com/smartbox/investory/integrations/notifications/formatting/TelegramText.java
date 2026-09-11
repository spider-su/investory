package com.smartbox.investory.integrations.notifications.formatting;

/** Small, shared builder for Telegram HTML messages. */
public final class TelegramText {
  private TelegramText() {}

  public static String escape(String value) {
    if (value == null) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  public static String heading(String icon, String title) {
    return "<b>" + escape(icon) + " " + escape(title) + "</b>";
  }

  public static String metric(String label, String value) {
    return "<b>" + escape(label) + ":</b> " + escape(value);
  }

  public static String link(String label, String url) {
    return "<a href=\"" + escape(url) + "\">" + escape(label) + "</a>";
  }
}
