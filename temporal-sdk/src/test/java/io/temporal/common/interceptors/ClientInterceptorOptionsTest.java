package io.temporal.common.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.temporal.client.ActivityClient;
import io.temporal.client.ActivityClientOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

/** Verifies that client interceptors receive the options of the client being created. */
public class ClientInterceptorOptionsTest {

  private static final String NAMESPACE = "interceptor-options-namespace";

  @Rule public SDKTestWorkflowRule testRule = SDKTestWorkflowRule.newBuilder().build();

  @Test
  public void workflowClientInterceptorReceivesClientOptions() {
    AtomicReference<String> namespace = new AtomicReference<>();
    AtomicBoolean legacyCalled = new AtomicBoolean();

    WorkflowClient.newInstance(
        testRule.getWorkflowServiceStubs(),
        WorkflowClientOptions.newBuilder()
            .setNamespace(NAMESPACE)
            .setInterceptors(
                new WorkflowClientInterceptorBase() {
                  @Override
                  public WorkflowClientCallsInterceptor workflowClientCallsInterceptor(
                      WorkflowClientOptions options, WorkflowClientCallsInterceptor next) {
                    namespace.set(options.getNamespace());
                    return next;
                  }
                },
                new WorkflowClientInterceptorBase() {
                  @Override
                  public WorkflowClientCallsInterceptor workflowClientCallsInterceptor(
                      WorkflowClientCallsInterceptor next) {
                    legacyCalled.set(true);
                    return next;
                  }
                })
            .validateAndBuildWithDefaults());

    assertEquals(NAMESPACE, namespace.get());
    assertTrue("Interceptors overriding only the legacy method are still used", legacyCalled.get());
  }

  @Test
  public void activityClientInterceptorReceivesClientOptions() {
    AtomicReference<String> namespace = new AtomicReference<>();
    AtomicBoolean legacyCalled = new AtomicBoolean();

    ActivityClient.newInstance(
        testRule.getWorkflowServiceStubs(),
        ActivityClientOptions.newBuilder()
            .setNamespace(NAMESPACE)
            .setInterceptors(
                Arrays.asList(
                    new ActivityClientInterceptorBase() {
                      @Override
                      public ActivityClientCallsInterceptor activityClientCallsInterceptor(
                          ActivityClientOptions options, ActivityClientCallsInterceptor next) {
                        namespace.set(options.getNamespace());
                        return next;
                      }
                    },
                    new ActivityClientInterceptorBase() {
                      @Override
                      public ActivityClientCallsInterceptor activityClientCallsInterceptor(
                          ActivityClientCallsInterceptor next) {
                        legacyCalled.set(true);
                        return next;
                      }
                    }))
            .build());

    assertEquals(NAMESPACE, namespace.get());
    assertTrue("Interceptors overriding only the legacy method are still used", legacyCalled.get());
  }
}
