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
package io.zeebe.protocol.impl.record.value.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.MessageRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public class MessageRecord extends UnifiedRecordValue implements MessageRecordValue {

  private final StringProperty nameProp = new StringProperty("name");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey");
  // TTL in milliseconds
  private final LongProperty timeToLiveProp = new LongProperty("timeToLive");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final StringProperty messageIdProp = new StringProperty("messageId", "");

  public MessageRecord() {
    this.declareProperty(nameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(timeToLiveProp)
        .declareProperty(variablesProp)
        .declareProperty(messageIdProp);
  }

  public boolean hasMessageId() {
    return messageIdProp.getValue().capacity() > 0;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getMessageIdBuffer() {
    return messageIdProp.getValue();
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  @Override
  public String getCorrelationKey() {
    return BufferUtil.bufferAsString(correlationKeyProp.getValue());
  }

  @Override
  public String getMessageId() {
    return BufferUtil.bufferAsString(messageIdProp.getValue());
  }

  public long getTimeToLive() {
    return timeToLiveProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  public MessageRecord setCorrelationKey(String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageRecord setCorrelationKey(DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageRecord setMessageId(String messageId) {
    messageIdProp.setValue(messageId);
    return this;
  }

  public MessageRecord setMessageId(DirectBuffer messageId) {
    messageIdProp.setValue(messageId);
    return this;
  }

  public MessageRecord setName(String name) {
    nameProp.setValue(name);
    return this;
  }

  public MessageRecord setName(DirectBuffer name) {
    nameProp.setValue(name);
    return this;
  }

  public MessageRecord setTimeToLive(long timeToLive) {
    timeToLiveProp.setValue(timeToLive);
    return this;
  }

  public MessageRecord setVariables(DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }
}
