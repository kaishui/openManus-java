package com.openmanus.prompt;


public abstract class BasePrompt implements Prompt {
  protected String systemPrompt;
  protected String nextStepPrompt;

  public BasePrompt(String systemPrompt, String nextStepPrompt) {
    this.systemPrompt = systemPrompt;
    this.nextStepPrompt = nextStepPrompt;
  }

  @Override
  public String getSystemPrompt() {
    return systemPrompt;
  }

  @Override
  public String getNextStepPrompt() {
    return nextStepPrompt;
  }

  public class ToolCallPrompt extends BasePrompt {
    public static final String SYSTEM_PROMPT = "You are a helpful assistant.";
    public static final String NEXT_STEP_PROMPT = "What is the next step to execute?";

    public ToolCallPrompt() {
      super(SYSTEM_PROMPT, NEXT_STEP_PROMPT);
    }
  }


  public class ManusPrompt extends BasePrompt {
    public static final String SYSTEM_PROMPT = "You are OpenManus, an all-capable AI assistant, aimed at solving any task presented by the user. You have various tools at your disposal that you can call upon to efficiently complete complex requests. Whether it's programming, information retrieval, file processing, or web browsing, you can handle it all.";
    public static final String NEXT_STEP_PROMPT = """
      You can interact with the computer using PythonExecute, save important content and information files through FileSaver, open browsers with BrowserUseTool, and retrieve information using GoogleSearch.

      PythonExecute: Execute Python code to interact with the computer system, data processing, automation tasks, etc.

      FileSaver: Save files locally, such as txt, py, html, etc.

      BrowserUseTool: Open, browse, and use web browsers.If you open a local HTML file, you must provide the absolute path to the file.

      GoogleSearch: Perform web information retrieval

      Based on user needs, proactively select the most appropriate tool or combination of tools. For complex tasks, you can break down the problem and use different tools step by step to solve it. After using each tool, clearly explain the execution results and suggest the next steps.
      """;

    public ManusPrompt() {
      super(SYSTEM_PROMPT, NEXT_STEP_PROMPT);
    }
  }


  public class SWEPrompt extends BasePrompt {
    public static final String SYSTEM_PROMPT = "You are SWE-Agent, an autonomous AI programmer that interacts directly with the computer to solve tasks.";
    public static final String NEXT_STEP_TEMPLATE = """
      You are currently in the directory: {current_dir}
      What is the next step to execute?
      """;

    public SWEPrompt() {
      super(SYSTEM_PROMPT, NEXT_STEP_TEMPLATE);
    }
  }


  public class PlanningPrompt extends BasePrompt {
    public static final String SYSTEM_PROMPT = "You are a planning assistant. Your task is to create a detailed plan with clear steps.";
    public static final String NEXT_STEP_PROMPT = "What is the next step to execute?";

    public PlanningPrompt() {
      super(SYSTEM_PROMPT, NEXT_STEP_PROMPT);
    }
  }
}


