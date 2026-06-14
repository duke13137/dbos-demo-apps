package org.example;

import dev.dbos.transact.DBOS;
import dev.dbos.transact.workflow.Workflow;

public class DurableWorkflowServiceImpl implements DurableWorkflowService {

  private final DBOS dbos;
  private DurableWorkflowService self;

  public DurableWorkflowServiceImpl(DBOS dbos) {
    this.dbos = dbos;
  }

  public void setSelf(DurableWorkflowService self) {
    this.self = self;
  }

  @Workflow
  @Override
  public Object exampleWorkflow() throws Exception {
    return ClojureFacade.invokeExampleWorkflow(dbos);
  }

  @Workflow
  @Override
  public Object bankTransferWorkflow() throws Exception {
    return ClojureFacade.invokeBankTransfer(dbos);
  }

  @Workflow
  @Override
  public Object debouncerWorkflow(String key) throws Exception {
    return ClojureFacade.invokeDebouncerWorkflow(dbos, key);
  }

  @Workflow
  @Override
  public Object queueWorkflow() throws Exception {
    return ClojureFacade.invokeQueueWorkflow(dbos, self);
  }

  @Workflow
  @Override
  public Object queueChildWorkflow(String workflowId, int step) throws Exception {
    return ClojureFacade.invokeQueueChildWorkflow(dbos, workflowId, step);
  }
}
