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
package io.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.BooleanProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.JobBatchRecordValue;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JobBatchRecord extends UnifiedRecordValue implements JobBatchRecordValue {

  private final StringProperty typeProp = new StringProperty("type");
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final LongProperty timeoutProp = new LongProperty("timeout", -1);
  private final IntegerProperty maxJobsToActivateProp =
      new IntegerProperty("maxJobsToActivate", -1);
  private final ArrayProperty<LongValue> jobKeysProp =
      new ArrayProperty<>("jobKeys", new LongValue());
  private final ArrayProperty<JobRecord> jobsProp = new ArrayProperty<>("jobs", new JobRecord());
  private final ArrayProperty<StringValue> variablesProp =
      new ArrayProperty<>("variables", new StringValue());
  private final BooleanProperty truncatedProp = new BooleanProperty("truncated", false);

  public JobBatchRecord() {
    this.declareProperty(typeProp)
        .declareProperty(workerProp)
        .declareProperty(timeoutProp)
        .declareProperty(maxJobsToActivateProp)
        .declareProperty(jobKeysProp)
        .declareProperty(jobsProp)
        .declareProperty(variablesProp)
        .declareProperty(truncatedProp);
  }

  public JobBatchRecord setType(DirectBuffer buf, int offset, int length) {
    typeProp.setValue(buf, offset, length);
    return this;
  }

  public JobBatchRecord setWorker(DirectBuffer worker, int offset, int length) {
    workerProp.setValue(worker, offset, length);
    return this;
  }

  public ValueArray<LongValue> jobKeys() {
    return jobKeysProp;
  }

  public ValueArray<JobRecord> jobs() {
    return jobsProp;
  }

  public ValueArray<StringValue> variables() {
    return variablesProp;
  }

  @Override
  public long getTimeout() {
    return timeoutProp.getValue();
  }

  public boolean getTruncated() {
    return truncatedProp.getValue();
  }

  @Override
  public String getType() {
    return BufferUtil.bufferAsString(typeProp.getValue());
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(workerProp.getValue());
  }

  public int getMaxJobsToActivate() {
    return maxJobsToActivateProp.getValue();
  }

  @Override
  public List<Long> getJobKeys() {
    return StreamSupport.stream(jobKeysProp.spliterator(), false)
        .map(LongValue::getValue)
        .collect(Collectors.toList());
  }

  @Override
  public List<JobRecordValue> getJobs() {
    return StreamSupport.stream(jobsProp.spliterator(), false)
        .map(
            jobRecord -> {
              final byte[] bytes = new byte[jobRecord.getLength()];
              final UnsafeBuffer copyRecord = new UnsafeBuffer(bytes);
              final JobRecord copiedRecord = new JobRecord();

              jobRecord.write(copyRecord, 0);
              copiedRecord.wrap(copyRecord);

              return copiedRecord;
            })
        .collect(Collectors.toList());
  }

  @Override
  public boolean isTruncated() {
    return truncatedProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getTypeBuffer() {
    return typeProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return workerProp.getValue();
  }

  public JobBatchRecord setMaxJobsToActivate(int maxJobsToActivate) {
    maxJobsToActivateProp.setValue(maxJobsToActivate);
    return this;
  }

  public JobBatchRecord setTimeout(long val) {
    timeoutProp.setValue(val);
    return this;
  }

  public JobBatchRecord setTruncated(boolean truncated) {
    truncatedProp.setValue(truncated);
    return this;
  }

  public JobBatchRecord setType(DirectBuffer buf) {
    this.typeProp.setValue(buf);
    return this;
  }

  public JobBatchRecord setType(String type) {
    this.typeProp.setValue(type);
    return this;
  }

  public JobBatchRecord setWorker(DirectBuffer worker) {
    this.workerProp.setValue(worker);
    return this;
  }

  public JobBatchRecord setWorker(String worker) {
    this.workerProp.setValue(worker);
    return this;
  }
}
