package io.temporal.opentracing.internal;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.temporal.common.interceptors.ActivityClientCallsInterceptor;
import io.temporal.common.interceptors.ActivityClientCallsInterceptorBase;
import io.temporal.opentracing.OpenTracingOptions;
import javax.annotation.Nullable;

public class OpenTracingActivityClientCallsInterceptor extends ActivityClientCallsInterceptorBase {
  private final SpanFactory spanFactory;
  private final Tracer tracer;
  private final ContextAccessor contextAccessor;
  private final @Nullable String namespace;

  public OpenTracingActivityClientCallsInterceptor(
      ActivityClientCallsInterceptor next,
      OpenTracingOptions options,
      SpanFactory spanFactory,
      ContextAccessor contextAccessor) {
    this(next, options, spanFactory, contextAccessor, null);
  }

  public OpenTracingActivityClientCallsInterceptor(
      ActivityClientCallsInterceptor next,
      OpenTracingOptions options,
      SpanFactory spanFactory,
      ContextAccessor contextAccessor,
      @Nullable String namespace) {
    super(next);
    this.namespace = namespace;
    this.spanFactory = spanFactory;
    this.tracer = options.getTracer();
    this.contextAccessor = contextAccessor;
  }

  @Override
  public StartActivityOutput startActivity(StartActivityInput input) {
    Span activityStartSpan =
        contextAccessor.writeSpanContextToHeader(
            () ->
                spanFactory
                    .createActivityStartSpan(
                        tracer,
                        namespace,
                        input.getActivityType(),
                        null,
                        null,
                        input.getOptions().getId())
                    .start(),
            input.getHeader(),
            tracer);
    try (Scope ignored = tracer.scopeManager().activate(activityStartSpan)) {
      return super.startActivity(input);
    } finally {
      activityStartSpan.finish();
    }
  }
}
