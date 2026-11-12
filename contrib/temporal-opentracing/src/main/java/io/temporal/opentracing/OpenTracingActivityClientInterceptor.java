package io.temporal.opentracing;

import io.temporal.client.ActivityClientOptions;
import io.temporal.common.interceptors.ActivityClientCallsInterceptor;
import io.temporal.common.interceptors.ActivityClientInterceptorBase;
import io.temporal.opentracing.internal.ContextAccessor;
import io.temporal.opentracing.internal.OpenTracingActivityClientCallsInterceptor;
import io.temporal.opentracing.internal.SpanFactory;

public class OpenTracingActivityClientInterceptor extends ActivityClientInterceptorBase {
  private final OpenTracingOptions options;
  private final SpanFactory spanFactory;
  private final ContextAccessor contextAccessor;
  private final boolean overridesSingleArgMethod;

  public OpenTracingActivityClientInterceptor() {
    this(OpenTracingOptions.getDefaultInstance());
  }

  public OpenTracingActivityClientInterceptor(OpenTracingOptions options) {
    this.options = options;
    this.spanFactory = new SpanFactory(options);
    this.contextAccessor = new ContextAccessor(options);
    this.overridesSingleArgMethod = overridesSingleArgMethod();
  }

  @Override
  public ActivityClientCallsInterceptor activityClientCallsInterceptor(
      ActivityClientCallsInterceptor next) {
    return new OpenTracingActivityClientCallsInterceptor(
        next, options, spanFactory, contextAccessor);
  }

  @Override
  public ActivityClientCallsInterceptor activityClientCallsInterceptor(
      ActivityClientOptions clientOptions, ActivityClientCallsInterceptor next) {
    if (overridesSingleArgMethod) {
      // Keep subclass customizations of the single-argument method working.
      return activityClientCallsInterceptor(next);
    }
    return new OpenTracingActivityClientCallsInterceptor(
        next, options, spanFactory, contextAccessor, clientOptions.getNamespace());
  }

  private boolean overridesSingleArgMethod() {
    try {
      return getClass()
              .getMethod("activityClientCallsInterceptor", ActivityClientCallsInterceptor.class)
              .getDeclaringClass()
          != OpenTracingActivityClientInterceptor.class;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }
}
