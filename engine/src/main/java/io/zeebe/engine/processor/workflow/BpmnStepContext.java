/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.TypedCommandWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;

public class BpmnStepContext<T extends ExecutableFlowElement> {

  private final IncidentRecord incidentCommand = new IncidentRecord();
  private final SideEffectQueue sideEffect = new SideEffectQueue();
  private final EventOutput eventOutput;
  private final MsgPackMergeTool mergeTool;
  private final WorkflowState stateDb;

  private long key;
  private WorkflowInstanceIntent intent;
  private WorkflowInstanceRecord recordValue;

  private ExecutableFlowElement element;
  private TypedCommandWriter commandWriter;

  public BpmnStepContext(WorkflowState stateDb, EventOutput eventOutput) {
    this.stateDb = stateDb;
    this.eventOutput = eventOutput;
    this.mergeTool = new MsgPackMergeTool(4096);
  }

  public WorkflowInstanceRecord getValue() {
    return recordValue;
  }

  public WorkflowInstanceIntent getState() {
    return intent;
  }

  public long getKey() {
    return key;
  }

  public void init(
      long key, final WorkflowInstanceRecord recordValue, WorkflowInstanceIntent intent) {
    this.recordValue = recordValue;
    this.key = key;
    this.intent = intent;
  }

  public T getElement() {
    return (T) element;
  }

  public void setElement(final ExecutableFlowElement element) {
    this.element = element;
  }

  public EventOutput getOutput() {
    return eventOutput;
  }

  public void setStreamWriter(final TypedStreamWriter streamWriter) {
    this.eventOutput.setStreamWriter(streamWriter);
    this.commandWriter = streamWriter;
  }

  public MsgPackMergeTool getMergeTool() {
    return mergeTool;
  }

  public TypedCommandWriter getCommandWriter() {
    return commandWriter;
  }

  public ElementInstance getFlowScopeInstance() {
    final WorkflowInstanceRecord value = getValue();
    return stateDb.getElementInstanceState().getInstance(value.getFlowScopeKey());
  }

  /**
   * can be null
   *
   * @return
   */
  public ElementInstance getElementInstance() {
    if (recordValue != null) {
      return stateDb.getElementInstanceState().getInstance(key);
    }
    return null;
  }

  public SideEffectQueue getSideEffect() {
    return sideEffect;
  }

  public void raiseIncident(ErrorType errorType, String errorMessage) {
    raiseIncident(errorType, key, errorMessage);
  }

  public void raiseIncident(ErrorType errorType, long variableScopeKey, String errorMessage) {
    incidentCommand.reset();

    incidentCommand
        .initFromWorkflowInstanceFailure(key, recordValue)
        .setErrorType(errorType)
        .setErrorMessage(errorMessage)
        .setVariableScopeKey(variableScopeKey);

    eventOutput.storeFailedRecord(key, recordValue, intent);
    commandWriter.appendNewCommand(IncidentIntent.CREATE, incidentCommand);
  }

  public WorkflowState getStateDb() {
    return stateDb;
  }

  public ElementInstanceState getElementInstanceState() {
    return stateDb.getElementInstanceState();
  }
}
