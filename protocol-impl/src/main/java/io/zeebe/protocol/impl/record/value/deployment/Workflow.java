/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.protocol.impl.record.value.deployment;

import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_KEY;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_VERSION;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class Workflow extends UnpackedObject implements DeployedWorkflow {
  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID);
  private final IntegerProperty versionProp = new IntegerProperty(PROP_WORKFLOW_VERSION);
  private final LongProperty keyProp = new LongProperty(PROP_WORKFLOW_KEY);
  private final StringProperty resourceNameProp = new StringProperty("resourceName");

  public Workflow() {
    this.declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(keyProp)
        .declareProperty(resourceNameProp);
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  @Override
  public long getWorkflowKey() {
    return getKey();
  }

  @JsonIgnore
  public long getKey() {
    return keyProp.getValue();
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(resourceNameProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  public Workflow setBpmnProcessId(DirectBuffer bpmnProcessId, int offset, int length) {
    this.bpmnProcessIdProp.setValue(bpmnProcessId, offset, length);
    return this;
  }

  public Workflow setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public Workflow setBpmnProcessId(DirectBuffer bpmnProcessId) {
    this.bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public Workflow setKey(long key) {
    this.keyProp.setValue(key);
    return this;
  }

  public Workflow setResourceName(String resourceName) {
    this.resourceNameProp.setValue(resourceName);
    return this;
  }

  public Workflow setResourceName(DirectBuffer resourceName) {
    this.resourceNameProp.setValue(resourceName);
    return this;
  }

  public Workflow setVersion(int version) {
    this.versionProp.setValue(version);
    return this;
  }
}
