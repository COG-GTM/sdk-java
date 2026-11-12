package io.temporal.opentracing;

import static org.junit.Assert.*;

import io.opentracing.Scope;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.ActivityClient;
import io.temporal.client.ActivityClientOptions;
import io.temporal.client.StartActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.interceptors.ActivityClientCallsInterceptor;
import io.temporal.common.interceptors.ActivityClientCallsInterceptorBase;
import io.temporal.common.interceptors.ActivityClientInterceptorBase;
import io.temporal.common.interceptors.WorkflowClientCallsInterceptor;
import io.temporal.common.interceptors.WorkflowClientCallsInterceptorBase;
import io.temporal.common.interceptors.WorkflowClientInterceptorBase;
import io.temporal.opentracing.internal.ActionTypeAndNameSpanBuilderProvider;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

public class NamespaceTagTest {

  private static final String CUSTOM_NAMESPACE = "custom-namespace";

  private static final MockTracer mockTracer =
      new MockTracer(new ThreadLocalScopeManager(), MockTracer.Propagator.TEXT_MAP);

  private final OpenTracingOptions OT_OPTIONS =
      OpenTracingOptions.newBuilder().setTracer(mockTracer).build();

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder()
          .setWorkflowClientOptions(
              WorkflowClientOptions.newBuilder()
                  .setInterceptors(new OpenTracingClientInterceptor(OT_OPTIONS))
                  .validateAndBuildWithDefaults())
          .setWorkerFactoryOptions(
              WorkerFactoryOptions.newBuilder()
                  .setWorkerInterceptors(new OpenTracingWorkerInterceptor(OT_OPTIONS))
                  .validateAndBuildWithDefaults())
          .setWorkflowTypes(WorkflowImpl.class, ChildWorkflowImpl.class)
          .setActivityImplementations(new ActivityImpl())
          .build();

  @After
  public void tearDown() {
    mockTracer.reset();
  }

  @ActivityInterface
  public interface TestActivity {
    @ActivityMethod
    String activity(String input);
  }

  @WorkflowInterface
  public interface TestWorkflow {
    @WorkflowMethod
    String workflow(String input);

    @SignalMethod
    void signal(String value);

    @QueryMethod
    String query();

    @UpdateMethod
    String update(String value);
  }

  @WorkflowInterface
  public interface TestChildWorkflow {
    @WorkflowMethod
    String childWorkflow(String input);
  }

  public static class ActivityImpl implements TestActivity {
    @Override
    public String activity(String input) {
      return input;
    }
  }

  public static class ChildWorkflowImpl implements TestChildWorkflow {
    @Override
    public String childWorkflow(String input) {
      return input;
    }
  }

  public static class WorkflowImpl implements TestWorkflow {
    private final TestActivity activity =
        Workflow.newActivityStub(
            TestActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(1)).build());

    private final TestActivity localActivity =
        Workflow.newLocalActivityStub(
            TestActivity.class,
            LocalActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(1))
                .build());

    private String signal;

    @Override
    public String workflow(String input) {
      String result =
          activity.activity(input)
              + localActivity.activity(input)
              + Workflow.newChildWorkflowStub(
                      TestChildWorkflow.class,
                      ChildWorkflowOptions.newBuilder()
                          .setWorkflowId(Workflow.getInfo().getWorkflowId() + "-child")
                          .build())
                  .childWorkflow(input);
      Workflow.await(() -> signal != null);
      return result + signal;
    }

    @Override
    public void signal(String value) {
      signal = value;
    }

    @Override
    public String query() {
      return "query";
    }

    @Override
    public String update(String value) {
      return value;
    }
  }

  @Test
  public void allSpansHaveNamespaceTag() {
    MockSpan span = mockTracer.buildSpan("ClientFunction").start();

    WorkflowClient client = testWorkflowRule.getWorkflowClient();
    String namespace = client.getOptions().getNamespace();
    try (Scope scope = mockTracer.scopeManager().activate(span)) {
      TestWorkflow workflow =
          client.newWorkflowStub(
              TestWorkflow.class,
              WorkflowOptions.newBuilder()
                  .setTaskQueue(testWorkflowRule.getTaskQueue())
                  .validateBuildWithDefaults());
      WorkflowClient.start(workflow::workflow, "a");
      assertEquals("query", workflow.query());
      assertEquals("u", workflow.update("u"));
      workflow.signal("s");
      assertEquals("aaas", WorkflowStub.fromTyped(workflow).getResult(String.class));
    } finally {
      span.finish();
    }

    List<MockSpan> spans = mockTracer.finishedSpans();
    Set<String> operationTypes = new HashSet<>();
    for (MockSpan s : spans) {
      if (s.operationName().equals("ClientFunction")) {
        continue;
      }
      operationTypes.add(s.operationName().split(":")[0]);
      assertEquals(
          "Span " + s.operationName() + " should have the namespace tag",
          namespace,
          s.tags().get(StandardTagNames.NAMESPACE));
    }

    for (SpanOperationType expected :
        new SpanOperationType[] {
          SpanOperationType.START_WORKFLOW,
          SpanOperationType.RUN_WORKFLOW,
          SpanOperationType.START_ACTIVITY,
          SpanOperationType.RUN_ACTIVITY,
          SpanOperationType.START_CHILD_WORKFLOW,
          SpanOperationType.QUERY_WORKFLOW,
          SpanOperationType.HANDLE_QUERY,
          SpanOperationType.UPDATE_WORKFLOW,
          SpanOperationType.HANDLE_UPDATE,
          SpanOperationType.SIGNAL_WORKFLOW,
          SpanOperationType.HANDLE_SIGNAL
        }) {
      assertTrue(
          "Expected a " + expected.getDefaultPrefix() + " span, got " + operationTypes,
          operationTypes.contains(expected.getDefaultPrefix()));
    }
  }

  @Test
  public void namespaceTagIsOmittedWhenNamespaceIsUnknown() {
    SpanCreationContext context =
        SpanCreationContext.newBuilder()
            .setSpanOperationType(SpanOperationType.START_WORKFLOW)
            .setActionName("TestWorkflow")
            .setWorkflowId("workflow-id")
            .build();
    MockSpan span =
        (MockSpan)
            ActionTypeAndNameSpanBuilderProvider.INSTANCE
                .createSpanBuilder(mockTracer, context)
                .start();
    span.finish();
    assertEquals("workflow-id", span.tags().get(StandardTagNames.WORKFLOW_ID));
    assertFalse(span.tags().containsKey(StandardTagNames.NAMESPACE));
  }

  @Test
  public void clientSpansUseTheClientNamespace() {
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            testWorkflowRule.getWorkflowServiceStubs(),
            WorkflowClientOptions.newBuilder()
                .setNamespace(CUSTOM_NAMESPACE)
                .setInterceptors(
                    new FakeServerWorkflowClientInterceptor(),
                    new OpenTracingClientInterceptor(OT_OPTIONS))
                .validateAndBuildWithDefaults());
    ActivityClient activityClient =
        ActivityClient.newInstance(
            testWorkflowRule.getWorkflowServiceStubs(),
            ActivityClientOptions.newBuilder()
                .setNamespace(CUSTOM_NAMESPACE)
                .setInterceptors(
                    Arrays.asList(
                        new FakeServerActivityClientInterceptor(),
                        new OpenTracingActivityClientInterceptor(OT_OPTIONS)))
                .build());

    workflowClient
        .newUntypedWorkflowStub(
            "TestWorkflow",
            WorkflowOptions.newBuilder()
                .setWorkflowId("custom-namespace-workflow")
                .setTaskQueue(testWorkflowRule.getTaskQueue())
                .build())
        .start("a");
    activityClient.start(
        "TestActivity",
        StartActivityOptions.newBuilder()
            .setId("custom-namespace-activity")
            .setTaskQueue(testWorkflowRule.getTaskQueue())
            .setScheduleToCloseTimeout(Duration.ofMinutes(1))
            .build(),
        "a");

    List<MockSpan> spans = mockTracer.finishedSpans();
    Set<String> operationNames = new HashSet<>();
    for (MockSpan s : spans) {
      operationNames.add(s.operationName());
      assertEquals(CUSTOM_NAMESPACE, s.tags().get(StandardTagNames.NAMESPACE));
    }
    assertEquals(
        new HashSet<>(Arrays.asList("StartWorkflow:TestWorkflow", "StartActivity:TestActivity")),
        operationNames);
  }

  @Test
  public void subclassOverridesOfSingleArgumentMethodsStillApply() {
    AtomicBoolean workflowOverrideCalled = new AtomicBoolean();
    AtomicBoolean activityOverrideCalled = new AtomicBoolean();

    WorkflowClient.newInstance(
        testWorkflowRule.getWorkflowServiceStubs(),
        WorkflowClientOptions.newBuilder()
            .setInterceptors(
                new OpenTracingClientInterceptor(OT_OPTIONS) {
                  @Override
                  public WorkflowClientCallsInterceptor workflowClientCallsInterceptor(
                      WorkflowClientCallsInterceptor next) {
                    workflowOverrideCalled.set(true);
                    return super.workflowClientCallsInterceptor(next);
                  }
                })
            .validateAndBuildWithDefaults());
    ActivityClient.newInstance(
        testWorkflowRule.getWorkflowServiceStubs(),
        ActivityClientOptions.newBuilder()
            .setInterceptors(
                Arrays.asList(
                    new OpenTracingActivityClientInterceptor(OT_OPTIONS) {
                      @Override
                      public ActivityClientCallsInterceptor activityClientCallsInterceptor(
                          ActivityClientCallsInterceptor next) {
                        activityOverrideCalled.set(true);
                        return super.activityClientCallsInterceptor(next);
                      }
                    }))
            .build());

    assertTrue(workflowOverrideCalled.get());
    assertTrue(activityOverrideCalled.get());
  }

  /** Answers start calls locally so client spans can be checked without a server round trip. */
  private static class FakeServerWorkflowClientInterceptor extends WorkflowClientInterceptorBase {
    @Override
    public WorkflowClientCallsInterceptor workflowClientCallsInterceptor(
        WorkflowClientCallsInterceptor next) {
      return new WorkflowClientCallsInterceptorBase(next) {
        @Override
        public WorkflowStartOutput start(WorkflowStartInput input) {
          return new WorkflowStartOutput(
              WorkflowExecution.newBuilder()
                  .setWorkflowId(input.getWorkflowId())
                  .setRunId("run-id")
                  .build());
        }
      };
    }
  }

  /** Answers start calls locally so client spans can be checked without a server round trip. */
  private static class FakeServerActivityClientInterceptor extends ActivityClientInterceptorBase {
    @Override
    public ActivityClientCallsInterceptor activityClientCallsInterceptor(
        ActivityClientCallsInterceptor next) {
      return new ActivityClientCallsInterceptorBase(next) {
        @Override
        public StartActivityOutput startActivity(StartActivityInput input) {
          return new StartActivityOutput(input.getOptions().getId(), null);
        }
      };
    }
  }
}
