package com.openmanus.prompt;


public interface Prompt {
  String getSystemPrompt();
  String getNextStepPrompt();
}
