package com.smartbox.investory.integrations.ai;

/** Provider-neutral conversational AI boundary used by delivery channels. */
public interface AiChat {
  String reply(String conversationId, String userMessage);

  void resetConversation(String conversationId);
}
