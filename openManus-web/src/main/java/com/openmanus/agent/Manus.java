package com.openmanus.agent;


import com.openmanus.llm.LLM;
import com.openmanus.tool.BrowserUseTool;
import com.openmanus.tool.FileSaver;
import com.openmanus.tool.Terminate;
import com.openmanus.tool.ToolCollection;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class Manus extends ToolCallAgent {
  private static final String SYSTEM_PROMPT = "You are Manus, a versatile agent capable of solving various tasks using multiple tools. You have access to tools for Python execution, web browsing, file operations, and information retrieval.";
  private static final String NEXT_STEP_PROMPT = "What is the next step to execute?";

  private static final List<String> SPECIAL_TOOL_NAMES = List.of(new Terminate().getName());

  public Manus(ToolCollection availableTools, LLM llm) {
    super(availableTools, llm);
    this.systemPrompt = SYSTEM_PROMPT;
    this.nextStepPrompt = NEXT_STEP_PROMPT;
    this.specialToolNames = SPECIAL_TOOL_NAMES;
  }

  public static ToolCollection getDefaultTools() {
    return new ToolCollection(
      new BrowserUseTool(),
      new FileSaver(),
      new Terminate()
    );
  }

  @Override
  public String getName() {
    return "Manus";
  }

  @Override
  public String getDescription() {
    return "A versatile agent that can solve various tasks using multiple tools";
  }
}
