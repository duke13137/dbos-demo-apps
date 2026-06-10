package org.example;

import dev.dbos.transact.DBOS;
import dev.dbos.transact.workflow.Workflow;

public class DurableWorkflowServiceImpl implements DurableWorkflowService {

  private final DBOS dbos;

  public DurableWorkflowServiceImpl(DBOS dbos) {
    this.dbos = dbos;
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
}
