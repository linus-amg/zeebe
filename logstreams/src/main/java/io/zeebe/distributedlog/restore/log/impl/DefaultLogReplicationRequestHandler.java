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
package io.zeebe.distributedlog.restore.log.impl;

import io.zeebe.distributedlog.restore.RestoreServer.LogReplicationRequestHandler;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class DefaultLogReplicationRequestHandler implements LogReplicationRequestHandler {
  private static final int DEFAULT_READ_BUFFER_SIZE = 64 * 1024 * 1024;

  private final LogStreamReader reader;
  private final MutableDirectBuffer readerBuffer;

  public DefaultLogReplicationRequestHandler(LogStream logStream) {
    this(logStream, DEFAULT_READ_BUFFER_SIZE);
  }

  public DefaultLogReplicationRequestHandler(LogStream logStream, int bufferSize) {
    this.reader = new BufferedLogStreamReader(logStream);
    this.readerBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
  }

  @Override
  public final LogReplicationResponse onReplicationRequest(
      LogReplicationRequest request, Logger logger) {
    final DefaultLogReplicationResponse response = new DefaultLogReplicationResponse();

    logger.debug("Received log replication request {}", request);
    if (seekToRequestedPosition(request.getFromPosition(), !request.includeFromPosition())) {
      long lastReadPosition = reader.getPosition();
      int offset = 0;

      while (reader.hasNext()) {
        final LoggedEvent event = reader.next();

        if ((offset + event.getLength()) > readerBuffer.capacity()) {
          break;
        }

        if (event.getPosition() <= request.getToPosition()) {
          event.write(readerBuffer, offset);
          offset += event.getLength();
          lastReadPosition = event.getPosition();
        }
      }

      response.setToPosition(lastReadPosition);
      response.setMoreAvailable(lastReadPosition < request.getToPosition() && reader.hasNext());
      response.setSerializedEvents(readerBuffer, 0, offset);
    } else {
      logger.debug(
          "Ignoring log replication request {} - {}, no events found with position {}",
          request.getFromPosition(),
          request.getToPosition(),
          request.getFromPosition());
    }

    logger.debug("Responding log replication request with {}", response);
    return response;
  }

  private boolean seekToRequestedPosition(long position, boolean skipEventAtPosition) {
    if (position == -1) {
      reader.seekToFirstEvent();
      return true;
    }

    if (reader.seek(position) && reader.hasNext()) {
      if (skipEventAtPosition) {
        reader.next();
      }
      return true;
    }

    return false;
  }
}
