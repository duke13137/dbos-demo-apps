package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.dbos.transact.DBOS;
import dev.dbos.transact.StartWorkflowOptions;
import dev.dbos.transact.execution.ThrowingRunnable;
import dev.dbos.transact.execution.ThrowingSupplier;
import dev.dbos.transact.workflow.WorkflowHandle;

import java.util.List;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class AppE2ETest {
  private static final String STEPS_EVENT = "steps_event";

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private static ThrowingSupplier<Object, Exception> anyObjectSupplier() {
    return (ThrowingSupplier) any();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private static ThrowingRunnable<Exception> anyRunnable() {
    return (ThrowingRunnable) any();
  }

  private static List<String> workflowEvents(DBOS dbos) {
    return mockingDetails(dbos).getInvocations().stream()
        .filter(invocation -> {
          var methodName = invocation.getMethod().getName();
          return "runStep".equals(methodName) || "setEvent".equals(methodName);
        })
        .map(invocation -> {
          var methodName = invocation.getMethod().getName();
          var args = invocation.getArguments();
          if ("runStep".equals(methodName)) {
            return "runStep:" + args[1];
          }
          return "setEvent:" + args[1];
        })
        .toList();
  }

  private static long invocationCount(DBOS dbos, String methodName) {
    return mockingDetails(dbos).getInvocations().stream()
        .filter(invocation -> methodName.equals(invocation.getMethod().getName()))
        .count();
  }

  @Test
  void exampleWorkflow_runsThreeStepsInOrderAndReturnsResult() throws Exception {
    var mockDBOS = mock(DBOS.class);
    var service = new DurableWorkflowServiceImpl(mockDBOS);

    var result = service.exampleWorkflow();

    assertEquals("workflow-completed", result);
    assertEquals(
        List.of(
            "runStep:stepOne",
            "setEvent:1",
            "runStep:stepTwo",
            "setEvent:2",
            "runStep:stepThree",
            "setEvent:3"),
        workflowEvents(mockDBOS));
  }

  @Test

  void bankTransferWorkflow_runsTwoStepsInOrderAndReturnsResult() throws Exception {
    var mockDBOS = mock(DBOS.class);
    var service = new DurableWorkflowServiceImpl(mockDBOS);

    var result = service.bankTransferWorkflow();

    assertEquals("bank-transfre-completed", result);
    assertEquals(
        List.of(
            "runStep:debt 100",
            "setEvent:100",
            "runStep:depot 200",
            "setEvent:200"),
        workflowEvents(mockDBOS));
  }

  @Test
  void debouncerWorkflow_runsWithoutInteractingWithDbos() throws Exception {
    var mockDBOS = mock(DBOS.class);
    var service = new DurableWorkflowServiceImpl(mockDBOS);

    var result = service.debouncerWorkflow("demo");

    assertEquals("debouncer-completed", result);
    verifyNoInteractions(mockDBOS);
  }

  @Test
  @SuppressWarnings({ "rawtypes", "unchecked" })
  void queueWorkflow_startsAndAwaitsQueuedChildren() throws Exception {
    var mockDBOS = mock(DBOS.class);
    var mockSelf = mock(DurableWorkflowService.class);
    WorkflowHandle mockHandle = mock(WorkflowHandle.class);

    when(mockDBOS.startWorkflow(anyObjectSupplier(), any(StartWorkflowOptions.class)))
        .thenReturn(mockHandle);
    when(mockDBOS.startWorkflow(anyRunnable(), any(StartWorkflowOptions.class))).thenReturn(mockHandle);
    when(mockDBOS.getEvent(anyString(), eq(STEPS_EVENT), any(Duration.class)))
        .thenReturn(Optional.empty());

    var service = new DurableWorkflowServiceImpl(mockDBOS);
    service.setSelf(mockSelf);

    var result = service.queueWorkflow();

    assertEquals("queue-completed", result);
    assertEquals(10, invocationCount(mockDBOS, "startWorkflow"));
    verify(mockHandle, times(10)).getResult();
    verify(mockDBOS).setEvent(eq(STEPS_EVENT), eq(Integer.valueOf(10)));
  }
}
