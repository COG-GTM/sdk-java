package io.temporal.common.interceptors;

import io.temporal.client.ActivityClientOptions;
import io.temporal.common.Experimental;

/**
 * Registered on {@link io.temporal.client.ActivityClientOptions} to intercept calls made by an
 * {@link io.temporal.client.ActivityClient}.
 *
 * <p>Prefer extending {@link ActivityClientInterceptorBase} and overriding only the methods you
 * need instead of implementing this interface directly.
 */
@Experimental
public interface ActivityClientInterceptor {

  /**
   * Called once during creation of an {@link io.temporal.client.ActivityClient} to create a chain
   * of {@link ActivityClientCallsInterceptor} instances.
   *
   * @param next next activity client interceptor in the chain of interceptors
   * @return a new {@link ActivityClientCallsInterceptor} that decorates calls to {@code next}
   */
  ActivityClientCallsInterceptor activityClientCallsInterceptor(
      ActivityClientCallsInterceptor next);

  /**
   * Called once during creation of an {@link io.temporal.client.ActivityClient} to create a chain
   * of {@link ActivityClientCallsInterceptor} instances. Unlike {@link
   * #activityClientCallsInterceptor(ActivityClientCallsInterceptor)}, this method also receives the
   * options of the client being created, for example to access its namespace.
   *
   * <p>The default implementation delegates to {@link
   * #activityClientCallsInterceptor(ActivityClientCallsInterceptor)}.
   *
   * @param options options of the {@link io.temporal.client.ActivityClient} being created
   * @param next next activity client interceptor in the chain of interceptors
   * @return a new {@link ActivityClientCallsInterceptor} that decorates calls to {@code next}
   */
  default ActivityClientCallsInterceptor activityClientCallsInterceptor(
      ActivityClientOptions options, ActivityClientCallsInterceptor next) {
    return activityClientCallsInterceptor(next);
  }
}
