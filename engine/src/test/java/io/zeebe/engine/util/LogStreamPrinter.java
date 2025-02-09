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

import static io.zeebe.engine.processor.TypedEventRegistry.EVENT_REGISTRY;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.ReflectUtil;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogStreamPrinter {

  private static final ServiceName<Object> PRINTER_SERVICE_NAME =
      ServiceName.newServiceName("printer", Object.class);

  private static final String HEADER_INDENTATION = "\t\t\t";
  private static final String ENTRY_INDENTATION = HEADER_INDENTATION + "\t";

  private static final Logger LOGGER = LoggerFactory.getLogger("io.zeebe.broker.test");

  public static void printRecords(final LogStream logStream) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Records on partition ");
    sb.append(logStream.getPartitionId());
    sb.append(":\n");

    final EnumMap<ValueType, UnpackedObject> eventCache = new EnumMap<>(ValueType.class);
    EVENT_REGISTRY.forEach((t, c) -> eventCache.put(t, ReflectUtil.newInstance(c)));

    try (LogStreamReader streamReader = new BufferedLogStreamReader(logStream)) {
      streamReader.seekToFirstEvent();

      while (streamReader.hasNext()) {
        final LoggedEvent event = streamReader.next();

        writeRecord(eventCache, event, sb);
      }
    }

    LOGGER.info(sb.toString());
  }

  private static void writeRecord(
      final Map<ValueType, UnpackedObject> eventCache,
      final LoggedEvent event,
      final StringBuilder sb) {
    sb.append(HEADER_INDENTATION);
    writeRecordHeader(event, sb);
    sb.append("\n");
    final RecordMetadata metadata = new RecordMetadata();
    event.readMetadata(metadata);
    sb.append(ENTRY_INDENTATION);
    writeMetadata(metadata, sb);
    sb.append("\n");

    final UnpackedObject unpackedObject = eventCache.get(metadata.getValueType());
    event.readValue(unpackedObject);
    sb.append(ENTRY_INDENTATION).append("Value:\n");
    unpackedObject.writeJSON(sb);
    sb.append("\n");
  }

  private static void writeRecordHeader(final LoggedEvent event, final StringBuilder sb) {
    sb.append("Position: ");
    sb.append(event.getPosition());
    sb.append(" Key: ");
    sb.append(event.getKey());
  }

  private static void writeMetadata(final RecordMetadata metadata, final StringBuilder sb) {
    sb.append(metadata.toString());
  }

  private static class PrinterService implements Service<Object> {

    private final Injector<LogStream> logStreamInjector = new Injector<>();

    @Override
    public Object get() {
      return this;
    }

    @Override
    public void start(final ServiceStartContext startContext) {
      final LogStream logStream = logStreamInjector.getValue();

      printRecords(logStream);
    }

    public Injector<LogStream> getLogStreamInjector() {
      return logStreamInjector;
    }
  }
}
