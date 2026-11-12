package io.temporal.common.interceptors;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.ActivityCompletionClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.Experimental;
import java.util.Optional;

@Experimental
public interface WorkflowClientInterceptor {
  /**
   * Called when workflow stub is instantiated during creation of new workflow. It allows to
   * decorate calls to {@link WorkflowStub} instance which is an entry point for client code.
   *
   * @return decorated stub
   * @deprecated consider implementing all intercepting functionality using {@link
   *     WorkflowClientCallsInterceptor} that is produced in {@link
   *     #workflowClientCallsInterceptor}. This method has to stay temporary because
   *     TimeLockingInterceptor has to intercept top level {@link WorkflowStub} methods.
   */
  @Deprecated
  WorkflowStub newUntypedWorkflowStub(
      String workflowType, WorkflowOptions options, WorkflowStub next);

  /**
   * Called when workflow stub is instantiated for a known existing workflow execution. It allows to
   * decorate calls to {@link WorkflowStub} instance which is an entry point for client code.
   *
   * @return decorated stub
   * @deprecated consider implementing all intercepting functionality using {@link
   *     WorkflowClientCallsInterceptor} that is produced in {@link
   *     #workflowClientCallsInterceptor}. This method has to stay temporary because
   *     TimeLockingInterceptor has to intercept top level {@link WorkflowStub} methods.
   */
  @Deprecated
  WorkflowStub newUntypedWorkflowStub(
      WorkflowExecution execution, Optional<String> workflowType, WorkflowStub next);

  ActivityCompletionClient newActivityCompletionClient(ActivityCompletionClient next);

  /**
   * Called once during creation of WorkflowClient to create a chain of Client Workflow Interceptors
   *
   * @param next next workflow client interceptor in the chain of interceptors
   * @return new interceptor that should decorate calls to {@code next}
   */
  WorkflowClientCallsInterceptor workflowClientCallsInterceptor(
      WorkflowClientCallsInterceptor next);

  /**
   * Called once during creation of WorkflowClient to create a chain of Client Workflow
   * Interceptors. Unlike {@link #workflowClientCallsInterceptor(WorkflowClientCallsInterceptor)},
   * this method also receives the options of the client being created, for example to access its
   * namespace.
   *
   * <p>The default implementation delegates to {@link
   * #workflowClientCallsInterceptor(WorkflowClientCallsInterceptor)}.
   *
   * @param options options of the WorkflowClient being created
   * @param next next workflow client interceptor in the chain of interceptors
   * @return new interceptor that should decorate calls to {@code next}
   */
  default WorkflowClientCallsInterceptor workflowClientCallsInterceptor(
      WorkflowClientOptions options, WorkflowClientCallsInterceptor next) {
    return workflowClientCallsInterceptor(next);
  }
}
