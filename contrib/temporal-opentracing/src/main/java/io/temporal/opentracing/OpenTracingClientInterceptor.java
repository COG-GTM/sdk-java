package io.temporal.opentracing;

import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.interceptors.WorkflowClientCallsInterceptor;
import io.temporal.common.interceptors.WorkflowClientInterceptorBase;
import io.temporal.opentracing.internal.ContextAccessor;
import io.temporal.opentracing.internal.OpenTracingWorkflowClientCallsInterceptor;
import io.temporal.opentracing.internal.SpanFactory;

public class OpenTracingClientInterceptor extends WorkflowClientInterceptorBase {
  private final OpenTracingOptions options;
  private final SpanFactory spanFactory;
  private final ContextAccessor contextAccessor;
  private final boolean overridesSingleArgMethod;

  public OpenTracingClientInterceptor() {
    this(OpenTracingOptions.getDefaultInstance());
  }

  public OpenTracingClientInterceptor(OpenTracingOptions options) {
    this.options = options;
    this.spanFactory = new SpanFactory(options);
    this.contextAccessor = new ContextAccessor(options);
    this.overridesSingleArgMethod = overridesSingleArgMethod();
  }

  @Override
  public WorkflowClientCallsInterceptor workflowClientCallsInterceptor(
      WorkflowClientCallsInterceptor next) {
    return new OpenTracingWorkflowClientCallsInterceptor(
        next, options, spanFactory, contextAccessor);
  }

  @Override
  public WorkflowClientCallsInterceptor workflowClientCallsInterceptor(
      WorkflowClientOptions clientOptions, WorkflowClientCallsInterceptor next) {
    if (overridesSingleArgMethod) {
      // Keep subclass customizations of the single-argument method working.
      return workflowClientCallsInterceptor(next);
    }
    return new OpenTracingWorkflowClientCallsInterceptor(
        next, options, spanFactory, contextAccessor, clientOptions.getNamespace());
  }

  private boolean overridesSingleArgMethod() {
    try {
      return getClass()
              .getMethod("workflowClientCallsInterceptor", WorkflowClientCallsInterceptor.class)
              .getDeclaringClass()
          != OpenTracingClientInterceptor.class;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }
}
