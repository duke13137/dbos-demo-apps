package org.example;

public interface DurableWorkflowService {
  Object exampleWorkflow() throws Exception;

  Object bankTransferWorkflow() throws Exception;

  Object debouncerWorkflow(String key) throws Exception;

  Object queueWorkflow() throws Exception;

  Object queueChildWorkflow(String workflowId, int step) throws Exception;
}
