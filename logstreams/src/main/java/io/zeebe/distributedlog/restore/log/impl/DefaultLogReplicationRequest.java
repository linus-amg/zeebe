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

import io.zeebe.distributedlog.restore.log.LogReplicationRequest;

public class DefaultLogReplicationRequest implements LogReplicationRequest {
  private long fromPosition;
  private long toPosition;
  private boolean includeFromPosition;

  public DefaultLogReplicationRequest() {}

  public DefaultLogReplicationRequest(long fromPosition, long toPosition) {
    this(fromPosition, toPosition, false);
  }

  public DefaultLogReplicationRequest(
      long fromPosition, long toPosition, boolean includeFromPosition) {
    this.fromPosition = fromPosition;
    this.toPosition = toPosition;
    this.includeFromPosition = includeFromPosition;
  }

  @Override
  public boolean includeFromPosition() {
    return includeFromPosition;
  }

  public void setIncludeFromPosition(boolean includeFromPosition) {
    this.includeFromPosition = includeFromPosition;
  }

  @Override
  public long getFromPosition() {
    return fromPosition;
  }

  public void setFromPosition(long fromPosition) {
    this.fromPosition = fromPosition;
  }

  @Override
  public long getToPosition() {
    return toPosition;
  }

  public void setToPosition(long toPosition) {
    this.toPosition = toPosition;
  }

  @Override
  public String toString() {
    return "DefaultLogReplicationRequest{"
        + "fromPosition="
        + fromPosition
        + ", toPosition="
        + toPosition
        + ", includeFromPosition="
        + includeFromPosition
        + '}';
  }
}
