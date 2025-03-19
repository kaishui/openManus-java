package com.openmanus.flow;


import com.openmanus.agent.BaseAgent;
import io.vertx.core.Future;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class BaseFlow {

  /*

  public BaseFlow(Map<String, BaseAgent> agents) {
    this.agents = new HashMap<>();
    this.primaryAgent = Optional.empty();

    if (agents != null) {
      if (agents.values().stream().findFirst().isPresent()) {
        this.primaryAgent = agents.values().stream().findFirst();
      }
      this.agents.putAll(agents);
    }
  }

  public BaseFlow(BaseAgent agent) {
    this.agents = new HashMap<>();
    this.primaryAgent = Optional.of(agent);
    this.agents.put("primary", agent);
  }

  public BaseFlow(List<BaseAgent> agentList) {
    this.agents = new HashMap<>();
    this.primaryAgent = Optional.empty();
    if (agentList != null && !agentList.isEmpty()) {
      this.primaryAgent = Optional.of(agentList.get(0));
      for (int i = 0; i < agentList.size(); i++) {
        this.agents.put("agent_" + i, agentList.get(i));
      }
    }
  }
*/
/*  public Map<String, BaseAgent> getAgents() {
    return agents;
  }

  public Optional<BaseAgent> getPrimaryAgent() {
    return primaryAgent;
  }*/

  public abstract Future<String> execute(String input);

  /*public void addAgent(String name, BaseAgent agent) {
    this.agents.put(name, agent);
    if (!this.primaryAgent.isPresent()) {
      this.primaryAgent = Optional.of(agent);
    }
  }

  public void addAgents(Map<String, BaseAgent> agents) {
    this.agents.putAll(agents);
    if (!this.primaryAgent.isPresent() && !agents.isEmpty()) {
      this.primaryAgent = agents.values().stream().findFirst();
    }
  }*/
}
