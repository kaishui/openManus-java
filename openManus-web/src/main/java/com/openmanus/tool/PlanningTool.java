package com.openmanus.tool;

import com.openmanus.tool.base.ToolResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class PlanningTool implements Tool {

  private static final String PLANNING_TOOL_DESCRIPTION = """
            A planning tool that allows the agent to create and manage plans for solving complex tasks.
            The tool provides functionality for creating plans, updating plan steps, and tracking progress.
            """;

  private final Map<String, JsonObject> plans = new HashMap<>(); // Dictionary to store plans by plan_id
  private String currentPlanId = null; // Track the current active plan

  // Add a getter for the plans map
  public Map<String, JsonObject> getPlans() {
    return plans;
  }

  @Override
  public String getName() {
    return "planning";
  }

  @Override
  public String getDescription() {
    return PLANNING_TOOL_DESCRIPTION;
  }

  @Override
  public JsonObject getParameters() {
    JsonObject parameters = new JsonObject();
    parameters.put("type", "object");
    JsonObject properties = new JsonObject();
    properties.put("command", new JsonObject()
      .put("description", "The command to execute. Available commands: create, update, list, get, set_active, mark_step, delete.")
      .put("enum", List.of("create", "update", "list", "get", "set_active", "mark_step", "delete"))
      .put("type", "string"));
    properties.put("plan_id", new JsonObject()
      .put("description", "Unique identifier for the plan. Required for create, update, set_active, and delete commands. Optional for get and mark_step (uses active plan if not specified).")
      .put("type", "string"));
    properties.put("title", new JsonObject()
      .put("description", "Title for the plan. Required for create command, optional for update command.")
      .put("type", "string"));
    properties.put("steps", new JsonObject()
      .put("description", "List of plan steps. Required for create command, optional for update command.")
      .put("type", "array")
      .put("items", new JsonObject().put("type", "string")));
    properties.put("step_index", new JsonObject()
      .put("description", "Index of the step to update (0-based). Required for mark_step command.")
      .put("type", "integer"));
    properties.put("step_status", new JsonObject()
      .put("description", "Status to set for a step. Used with mark_step command.")
      .put("enum", List.of("not_started", "in_progress", "completed", "blocked"))
      .put("type", "string"));
    properties.put("step_notes", new JsonObject()
      .put("description", "Additional notes for a step. Optional for mark_step command.")
      .put("type", "string"));
    parameters.put("properties", properties);
    JsonArray required = new JsonArray().add("command");
    parameters.put("required", required);
    parameters.put("additionalProperties", false);
    return parameters;
  }

  @Override
  public Future<Object> execute(JsonObject toolInput) {
    Promise<Object> promise = Promise.promise();
    String command = toolInput.getString("command");
    String planId = toolInput.getString("plan_id");
    String title = toolInput.getString("title");
    JsonArray stepsJson = toolInput.getJsonArray("steps");
    List<String> steps = stepsJson != null ? stepsJson.getList() : null;
    Integer stepIndex = toolInput.getInteger("step_index");
    String stepStatus = toolInput.getString("step_status");
    String stepNotes = toolInput.getString("step_notes");

    try {
      switch (command) {
        case "create":
          promise.complete(createPlan(planId, title, steps).toJson());
          break;
        case "update":
          promise.complete(updatePlan(planId, title, steps).toJson());
          break;
        case "list":
          promise.complete(listPlans().toJson());
          break;
        case "get":
          promise.complete(getPlan(planId).toJson());
          break;
        case "set_active":
          promise.complete(setActivePlan(planId).toJson());
          break;
        case "mark_step":
          promise.complete(markStep(planId, stepIndex, stepStatus, stepNotes).toJson());
          break;
        case "delete":
          promise.complete(deletePlan(planId).toJson());
          break;
        default:
          promise.complete(new ToolResult("Unrecognized command: " + command + ". Allowed commands are: create, update, list, get, set_active, mark_step, delete").toJson());
      }
    } catch (Exception e) {
      promise.complete(new ToolResult("Error: " + e.getMessage()).toJson());
    }
    return promise.future();
  }

  private ToolResult createPlan(String planId, String title, List<String> steps) {
    if (planId == null) {
      return new ToolResult("Error: Parameter `plan_id` is required for command: create");
    }

    if (plans.containsKey(planId)) {
      return new ToolResult("Error: A plan with ID '" + planId + "' already exists. Use 'update' to modify existing plans.");
    }

    if (title == null) {
      return new ToolResult("Error: Parameter `title` is required for command: create");
    }

    if (steps == null || steps.isEmpty()) {
      return new ToolResult("Error: Parameter `steps` must be a non-empty list of strings for command: create");
    }

    // Create a new plan with initialized step statuses
    JsonObject plan = new JsonObject();
    plan.put("plan_id", planId);
    plan.put("title", title);
    plan.put("steps", new JsonArray(steps));
    List<String> stepStatuses = new ArrayList<>();
    List<String> stepNotes = new ArrayList<>();
    for (int i = 0; i < steps.size(); i++) {
      stepStatuses.add("not_started");
      stepNotes.add("");
    }
    plan.put("step_statuses", new JsonArray(stepStatuses));
    plan.put("step_notes", new JsonArray(stepNotes));

    plans.put(planId, plan);
    currentPlanId = planId; // Set as active plan

    return new ToolResult("Plan created successfully with ID: " + planId + "\n\n" + formatPlan(plan));
  }

  private ToolResult updatePlan(String planId, String title, List<String> steps) {
    if (planId == null) {
      return new ToolResult("Error: Parameter `plan_id` is required for command: update");
    }

    JsonObject plan = plans.get(planId);
    if (plan == null) {
      return new ToolResult("Error: No plan found with ID: " + planId);
    }

    if (title != null) {
      plan.put("title", title);
    }

    if (steps != null) {
      // Preserve existing step statuses for unchanged steps
      JsonArray oldSteps = plan.getJsonArray("steps");
      JsonArray oldStatuses = plan.getJsonArray("step_statuses");
      JsonArray oldNotes = plan.getJsonArray("step_notes");

      // Create new step statuses and notes
      List<String> newStatuses = new ArrayList<>();
      List<String> newNotes = new ArrayList<>();

      for (int i = 0; i < steps.size(); i++) {
        String step = steps.get(i);
        // If the step exists at the same position in old steps, preserve status and notes
        if (i < oldSteps.size() && step.equals(oldSteps.getString(i))) {
          newStatuses.add(oldStatuses.getString(i));
          newNotes.add(oldNotes.getString(i));
        } else {
          newStatuses.add("not_started");
          newNotes.add("");
        }
      }

      plan.put("steps", new JsonArray(steps));
      plan.put("step_statuses", new JsonArray(newStatuses));
      plan.put("step_notes", new JsonArray(newNotes));
    }

    return new ToolResult("Plan updated successfully: " + planId + "\n\n" + formatPlan(plan));
  }

  public ToolResult listPlans() {
    if (plans.isEmpty()) {
      return new ToolResult("No plans available. Create a plan with the 'create' command.");
    }

    StringBuilder output = new StringBuilder("Available plans:\n");
    for (Map.Entry<String, JsonObject> entry : plans.entrySet()) {
      String planId = entry.getKey();
      JsonObject plan = entry.getValue();
      String currentMarker = planId.equals(currentPlanId) ? " (active)" : "";
      JsonArray stepStatuses = plan.getJsonArray("step_statuses");
      int completed = 0;
      for (int i = 0; i < stepStatuses.size(); i++) {
        if (stepStatuses.getString(i).equals("completed")) {
          completed++;
        }
      }
      int total = plan.getJsonArray("steps").size();
      String progress = completed + "/" + total + " steps completed";
      output.append("• ").append(planId).append(currentMarker).append(": ").append(plan.getString("title")).append(" - ").append(progress).append("\n");
    }

    return new ToolResult(output.toString());
  }

  private ToolResult getPlan(String planId) {
    if (planId == null) {
      // If no plan_id is provided, use the current active plan
      if (currentPlanId == null) {
        return new ToolResult("Error: No active plan. Please specify a plan_id or set an active plan.");
      }
      planId = currentPlanId;
    }

    JsonObject plan = plans.get(planId);
    if (plan == null) {
      return new ToolResult("Error: No plan found with ID: " + planId);
    }

    return new ToolResult(formatPlan(plan));
  }

  private ToolResult setActivePlan(String planId) {
    if (planId == null) {
      return new ToolResult("Error: Parameter `plan_id` is required for command: set_active");
    }

    if (!plans.containsKey(planId)) {
      return new ToolResult("Error: No plan found with ID: " + planId);
    }

    currentPlanId = planId;
    return new ToolResult("Plan '" + planId + "' is now the active plan.\n\n" + formatPlan(plans.get(planId)));
  }

  private ToolResult markStep(String planId, Integer stepIndex, String stepStatus, String stepNotes) {
    if (planId == null) {
      // If no plan_id is provided, use the current active plan
      if (currentPlanId == null) {
        return new ToolResult("Error: No active plan. Please specify a plan_id or set an active plan.");
      }
      planId = currentPlanId;
    }

    JsonObject plan = plans.get(planId);
    if (plan == null) {
      return new ToolResult("Error: No plan found with ID: " + planId);
    }

    if (stepIndex == null) {
      return new ToolResult("Error: Parameter `step_index` is required for command: mark_step");
    }

    if (stepIndex < 0 || stepIndex >= plan.getJsonArray("steps").size()) {
      return new ToolResult("Error: Invalid step_index: " + stepIndex + ". Valid indices range from 0 to " + (plan.getJsonArray("steps").size() - 1) + ".");
    }

    if (stepStatus != null && !List.of("not_started", "in_progress", "completed", "blocked").contains(stepStatus)) {
      return new ToolResult("Error: Invalid step_status: " + stepStatus + ". Valid statuses are: not_started, in_progress, completed, blocked");
    }

    if (stepStatus != null) {
      plan.getJsonArray("step_statuses").set(stepIndex, stepStatus);
    }

    if (stepNotes != null) {
      plan.getJsonArray("step_notes").set(stepIndex, stepNotes);
    }

    return new ToolResult("Step " + stepIndex + " updated in plan '" + planId + "'.\n\n" + formatPlan(plan));
  }

  private ToolResult deletePlan(String planId) {
    if (planId == null) {
      return new ToolResult("Error: Parameter `plan_id` is required for command: delete");
    }

    if (!plans.containsKey(planId)) {
      return new ToolResult("Error: No plan found with ID: " + planId);
    }

    plans.remove(planId);

    // If the deleted plan was the active plan, clear the active plan
    if (currentPlanId != null && currentPlanId.equals(planId)) {
      currentPlanId = null;
    }

    return new ToolResult("Plan '" + planId + "' has been deleted.");
  }

  private String formatPlan(JsonObject plan) {
    StringBuilder output = new StringBuilder();
    output.append("Plan: ").append(plan.getString("title")).append(" (ID: ").append(plan.getString("plan_id")).append(")\n");
    for (int i = 0; i < ("Plan: " + plan.getString("title") + " (ID: " + plan.getString("plan_id") + ")").length(); i++) {
      output.append("=");
    }
    output.append("\n\n");

    // Calculate progress statistics
    JsonArray steps = plan.getJsonArray("steps");
    JsonArray stepStatuses = plan.getJsonArray("step_statuses");
    int totalSteps = steps.size();
    int completed = 0;
    int inProgress = 0;
    int blocked = 0;
    int notStarted = 0;
    for (int i = 0; i < stepStatuses.size(); i++) {
      String status = stepStatuses.getString(i);
      switch (status) {
        case "completed":
          completed++;
          break;
        case "in_progress":
          inProgress++;
          break;
        case "blocked":
          blocked++;
          break;
        case "not_started":
          notStarted++;
          break;
      }
    }

    output.append("Progress: ").append(completed).append("/").append(totalSteps).append(" steps completed ");
    if (totalSteps > 0) {
      double percentage = ((double) completed / totalSteps) * 100;
      output.append(String.format("(%.1f%%)", percentage));
    } else {
      output.append("(0%)");
    }
    output.append("\n");

    output.append("Status: ").append(completed).append(" completed, ").append(inProgress).append(" in progress, ").append(blocked).append(" blocked, ").append(notStarted).append(" not started\n\n");
    output.append("Steps:\n");

    // Add each step with its status and notes
    JsonArray stepNotes = plan.getJsonArray("step_notes");
    for (int i = 0; i < steps.size(); i++) {
      String step = steps.getString(i);
      String status = stepStatuses.getString(i);
      String notes = stepNotes.getString(i);
      String statusSymbol = switch (status) {
        case "not_started" -> "[ ]";
        case "in_progress" -> "[→]";
        case "completed" -> "[✓]";
        case "blocked" -> "[!]";
        default -> "[]";
      };

      output.append(i).append(". ").append(statusSymbol).append(" ").append(step).append("\n");
      if (!notes.isEmpty()) {
        output.append("   Notes: ").append(notes).append("\n");
      }
    }

    return output.toString();
  }
}
