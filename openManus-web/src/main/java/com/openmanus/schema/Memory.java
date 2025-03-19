package com.openmanus.schema;

import io.vertx.core.json.JsonArray;

import java.util.ArrayList;
import java.util.List;

public class Memory {
  private List<Message> messages = new ArrayList<>();
  private int maxMessages = 100;

  public Memory() {
  }

  public List<Message> getMessages() {
    return messages;
  }

  public void setMessages(List<Message> messages) {
    this.messages = messages;
  }

  public int getMaxMessages() {
    return maxMessages;
  }

  public void setMaxMessages(int maxMessages) {
    this.maxMessages = maxMessages;
  }

  public void addMessage(Message message) {
    this.messages.add(message);
    if (this.messages.size() > this.maxMessages) {
      this.messages = this.messages.subList(this.messages.size() - this.maxMessages, this.messages.size());
    }
  }

  public void addMessages(List<Message> messages) {
    this.messages.addAll(messages);
  }

  public void clear() {
    this.messages.clear();
  }

  public List<Message> getRecentMessages(int n) {
    if (n >= this.messages.size()) {
      return this.messages;
    }
    return this.messages.subList(this.messages.size() - n, this.messages.size());
  }

  public JsonArray toJsonArray() {
    JsonArray jsonArray = new JsonArray();
    for (Message message : this.messages) {
      jsonArray.add(message.toJson());
    }
    return jsonArray;
  }
}
