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
package io.zeebe.engine.util;

import io.zeebe.engine.processor.CopiedRecords;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.stream.StreamWrapper;
import io.zeebe.util.buffer.BufferUtil;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;

public class RecordStream extends StreamWrapper<LoggedEvent, RecordStream> {

  public RecordStream(final Stream<LoggedEvent> stream) {
    super(stream);
  }

  @Override
  protected RecordStream supply(final Stream<LoggedEvent> wrappedStream) {
    return new RecordStream(wrappedStream);
  }

  public RecordStream withIntent(final Intent intent) {
    return new RecordStream(filter(r -> Records.hasIntent(r, intent)));
  }

  public TypedRecordStream<JobRecord> onlyJobRecords() {
    return new TypedRecordStream<>(
        filter(Records::isJobRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<IncidentRecord> onlyIncidentRecords() {
    return new TypedRecordStream<>(
        filter(Records::isIncidentRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<DeploymentRecord> onlyDeploymentRecords() {
    return new TypedRecordStream<>(
        filter(Records::isDeploymentRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<WorkflowInstanceRecord> onlyWorkflowInstanceRecords() {
    return new TypedRecordStream<>(
        filter(Records::isWorkflowInstanceRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<MessageRecord> onlyMessageRecords() {
    return new TypedRecordStream<>(
        filter(Records::isMessageRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<MessageSubscriptionRecord> onlyMessageSubscriptionRecords() {
    return new TypedRecordStream<>(
        filter(Records::isMessageSubscriptionRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<MessageStartEventSubscriptionRecord>
      onlyMessageStartEventSubscriptionRecords() {
    return new TypedRecordStream<>(
        filter(Records::isMessageStartEventSubscriptionRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<WorkflowInstanceSubscriptionRecord>
      onlyWorkflowInstanceSubscriptionRecords() {
    return new TypedRecordStream<>(
        filter(Records::isWorkflowInstanceSubscriptionRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<TimerRecord> onlyTimerRecords() {
    return new TypedRecordStream<>(
        filter(Records::isTimerRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<WorkflowInstanceCreationRecord> onlyWorkflowInstanceCreationRecords() {
    return new TypedRecordStream<>(
        filter(Records::isWorkflowInstanceCreationRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<ErrorRecord> onlyErrorRecords() {
    return new TypedRecordStream<>(
        filter(Records::isErrorRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  /**
   * This method makes only sense when the stream contains only entries of one workflow instance and
   * the element is only instantiated once within that instance.
   */
  public Stream<WorkflowInstanceIntent> onlyStatesOf(final String elementId) {
    final DirectBuffer elementIdBuffer = BufferUtil.wrapString(elementId);

    return onlyWorkflowInstanceRecords()
        .onlyEvents()
        .filter(r -> elementIdBuffer.equals(r.getValue().getElementIdBuffer()))
        .map(r -> (WorkflowInstanceIntent) r.getIntent());
  }
}
