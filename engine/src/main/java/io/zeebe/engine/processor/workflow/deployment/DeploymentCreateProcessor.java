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
package io.zeebe.engine.processor.workflow.deployment;

import io.atomix.core.Atomix;
import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.DeploymentIntent;

public class DeploymentCreateProcessor implements TypedRecordProcessor<DeploymentRecord> {
  public static final String DEPLOYMENT_ALREADY_EXISTS_MESSAGE =
      "Expected to create a new deployment with key '%d', but there is already an existing deployment with that key";

  private final WorkflowState workflowState;
  private final Atomix atomix;
  private final int partitionId;

  public DeploymentCreateProcessor(
      final WorkflowState workflowState, final Atomix atomix, final int partitionId) {
    this.workflowState = workflowState;
    this.atomix = atomix;
    this.partitionId = partitionId;
  }

  @Override
  public void processRecord(
      final TypedRecord<DeploymentRecord> event,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final DeploymentRecord deploymentEvent = event.getValue();
    if (workflowState.putDeployment(event.getKey(), deploymentEvent)) {
      streamWriter.appendFollowUpEvent(event.getKey(), DeploymentIntent.CREATED, deploymentEvent);
    } else {
      streamWriter.appendRejection(
          event,
          RejectionType.ALREADY_EXISTS,
          String.format(DEPLOYMENT_ALREADY_EXISTS_MESSAGE, event.getKey()));
    }

    final PushDeploymentResponse response =
        new PushDeploymentResponse().deploymentKey(event.getKey()).partitionId(partitionId);
    final String topic = String.format("deployment-response-%d-%d", partitionId, event.getKey());

    atomix.getEventService().broadcast(topic, response.toBytes());
    Loggers.WORKFLOW_PROCESSOR_LOGGER.debug("Send deployment response on topic {}", topic);
  }
}
