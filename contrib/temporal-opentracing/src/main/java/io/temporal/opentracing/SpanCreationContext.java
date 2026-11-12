package io.temporal.opentracing;

import javax.annotation.Nullable;

/**
 * Used when creating an OpenTracing span and provides contextual information used for naming and
 * tagging OT spans.
 */
public class SpanCreationContext {

  private final SpanOperationType spanOperationType;
  private final String actionName;
  private final String workflowId;
  private final String runId;
  private final String parentWorkflowId;
  private final String parentRunId;
  private final String activityId;
  private final String namespace;

  private SpanCreationContext(
      SpanOperationType spanOperationType,
      String actionName,
      String workflowId,
      String runId,
      String parentWorkflowId,
      String parentRunId,
      String activityId,
      String namespace) {
    this.spanOperationType = spanOperationType;
    this.actionName = actionName;
    this.workflowId = workflowId;
    this.runId = runId;
    this.parentWorkflowId = parentWorkflowId;
    this.parentRunId = parentRunId;
    this.activityId = activityId;
    this.namespace = namespace;
  }

  public SpanOperationType getSpanOperationType() {
    return spanOperationType;
  }

  /**
   * Returns the action name, which is the name of the Workflow or Activity class
   *
   * @return The action name
   */
  public String getActionName() {
    return actionName;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public @Nullable String getRunId() {
    return runId;
  }

  public String getParentWorkflowId() {
    return parentWorkflowId;
  }

  public String getParentRunId() {
    return parentRunId;
  }

  public @Nullable String getActivityId() {
    return activityId;
  }

  /**
   * Returns the namespace the traced operation runs in: the client's namespace for client-side
   * spans, and the worker's namespace for worker-side spans.
   *
   * @return The namespace, or null if it is not known
   */
  public @Nullable String getNamespace() {
    return namespace;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    private SpanOperationType spanOperationType;
    private String actionName;
    private String workflowId;
    private String runId;
    private String parentWorkflowId;
    private String parentRunId;
    private String activityId;
    private String namespace;

    private Builder() {}

    public Builder setSpanOperationType(SpanOperationType spanOperationType) {
      this.spanOperationType = spanOperationType;
      return this;
    }

    public Builder setActionName(String actionName) {
      this.actionName = actionName;
      return this;
    }

    public Builder setWorkflowId(String workflowId) {
      this.workflowId = workflowId;
      return this;
    }

    public Builder setRunId(String runId) {
      this.runId = runId;
      return this;
    }

    public Builder setParentWorkflowId(String parentWorkflowId) {
      this.parentWorkflowId = parentWorkflowId;
      return this;
    }

    public Builder setParentRunId(String parentRunId) {
      this.parentRunId = parentRunId;
      return this;
    }

    public Builder setActivityId(String activityId) {
      this.activityId = activityId;
      return this;
    }

    public Builder setNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public SpanCreationContext build() {
      return new SpanCreationContext(
          spanOperationType,
          actionName,
          workflowId,
          runId,
          parentWorkflowId,
          parentRunId,
          activityId,
          namespace);
    }
  }
}
