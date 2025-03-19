package com.openmanus.agent;

import com.openmanus.llm.LLM;
import com.openmanus.schema.AgentState;
import com.openmanus.schema.Memory;
import com.openmanus.schema.Message;
import com.openmanus.tool.ToolCollection;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseAgent {
  protected ToolCollection availableTools;
  protected List<Message> messages = new ArrayList<>();
  protected Memory memory = new Memory();
  protected LLM llm;
  protected AgentState state = AgentState.IDLE;

  public BaseAgent(ToolCollection availableTools, LLM llm) {
    this.availableTools = availableTools;
    this.llm = llm;
  }

  public abstract Future<Boolean> think();

  public abstract Future<String> act();

  public Future<String> run(String input) {
    state = AgentState.RUNNING;
    memory.addMessage(Message.userMessage(input));
    return think()
      .compose(success -> {
        if (success) {
          return act();
        } else {
          state = AgentState.FINISHED;
          return Future.succeededFuture("think failed");
        }
      })
      .onFailure(throwable -> state = AgentState.ERROR);
  }

  public AgentState getState() {
    return state;
  }

  public void setState(AgentState state) {
    this.state = state;
  }

  public Memory getMemory() {
    return memory;
  }

  public void setMemory(Memory memory) {
    this.memory = memory;
  }

  public ToolCollection getAvailableTools() {
    return availableTools;
  }

  public void setAvailableTools(ToolCollection availableTools) {
    this.availableTools = availableTools;
  }

  public List<Message> getMessages() {
    return messages;
  }

  public void setMessages(List<Message> messages) {
    this.messages = messages;
  }
}
