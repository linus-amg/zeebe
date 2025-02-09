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
package io.zeebe.engine.util.client;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.value.MessageRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PublishMessageClient {

  private static final int DEFAULT_VALUE = -1;
  private static final Duration DEFAULT_MSG_TTL = Duration.ofHours(1);

  private static final Function<Message, Record<MessageRecordValue>>
      SUCCESSFUL_EXPECTATION_SUPPLIER =
          (message) ->
              RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                  .withPartitionId(message.partitionId)
                  .withCorrelationKey(message.correlationKey)
                  .withSourceRecordPosition(message.position)
                  .getFirst();

  private static final Function<Message, Record<MessageRecordValue>>
      REJECTION_EXPECTATION_SUPPLIER =
          (message) ->
              RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                  .onlyCommandRejections()
                  .withPartitionId(message.partitionId)
                  .withCorrelationKey(message.correlationKey)
                  .getFirst();

  private final MessageRecord messageRecord;
  private final StreamProcessorRule enviromentRule;
  private final int partitionCount;

  private Function<Message, Record<MessageRecordValue>> expectation =
      SUCCESSFUL_EXPECTATION_SUPPLIER;
  private int partitionId = DEFAULT_VALUE;

  public PublishMessageClient(StreamProcessorRule environmentRule, int partitionCount) {
    this.enviromentRule = environmentRule;
    this.partitionCount = partitionCount;

    messageRecord = new MessageRecord();
    messageRecord.setTimeToLive(DEFAULT_MSG_TTL.toMillis());
  }

  public PublishMessageClient withCorrelationKey(String correlationKey) {
    messageRecord.setCorrelationKey(correlationKey);
    return this;
  }

  public PublishMessageClient withName(String name) {
    messageRecord.setName(name);
    return this;
  }

  public PublishMessageClient withId(String id) {
    messageRecord.setMessageId(id);
    return this;
  }

  public PublishMessageClient withTimeToLive(long timeToLive) {
    messageRecord.setTimeToLive(timeToLive);
    return this;
  }

  public PublishMessageClient withVariables(DirectBuffer variables) {
    messageRecord.setVariables(variables);
    return this;
  }

  public PublishMessageClient withVariables(String variables) {
    messageRecord.setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
    return this;
  }

  public PublishMessageClient onPartition(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public PublishMessageClient expectRejection() {
    expectation = REJECTION_EXPECTATION_SUPPLIER;
    return this;
  }

  public Record<MessageRecordValue> publish() {

    if (partitionId == DEFAULT_VALUE) {
      partitionId =
          SubscriptionUtil.getSubscriptionPartitionId(
              messageRecord.getCorrelationKeyBuffer(), partitionCount);
    }

    final long position =
        enviromentRule.writeCommandOnPartition(partitionId, MessageIntent.PUBLISH, messageRecord);

    return expectation.apply(new Message(partitionId, messageRecord.getCorrelationKey(), position));
  }

  private class Message {

    final int partitionId;
    final String correlationKey;
    final long position;

    Message(int partitionId, String correlationKey, long position) {
      this.partitionId = partitionId;
      this.correlationKey = correlationKey;
      this.position = position;
    }
  }
}
