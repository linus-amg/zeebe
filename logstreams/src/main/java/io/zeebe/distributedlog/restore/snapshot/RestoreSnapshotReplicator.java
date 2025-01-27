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
package io.zeebe.distributedlog.restore.snapshot;

import io.atomix.cluster.MemberId;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.snapshot.impl.DefaultSnapshotRestoreRequest;
import io.zeebe.logstreams.state.SnapshotConsumer;
import io.zeebe.util.ZbLogger;
import io.zeebe.util.collection.Tuple;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class RestoreSnapshotReplicator {

  private final RestoreClient client;
  private final SnapshotRestoreContext restoreContext;
  private SnapshotConsumer snapshotConsumer;
  private final Executor executor;
  private final Logger logger;
  private int numChunks;

  public RestoreSnapshotReplicator(
      RestoreClient client,
      SnapshotRestoreContext restoreContext,
      SnapshotConsumer snapshotConsumer,
      Executor executor,
      Logger logger) {
    this.client = client;
    this.restoreContext = restoreContext;
    this.snapshotConsumer = snapshotConsumer;
    this.executor = executor;
    this.logger = logger;
  }

  public RestoreSnapshotReplicator(
      RestoreClient client,
      SnapshotRestoreContext restoreContext,
      SnapshotConsumer snapshotConsumer,
      Executor executor) {
    this(
        client,
        restoreContext,
        snapshotConsumer,
        executor,
        new ZbLogger(RestoreSnapshotReplicator.class));
  }

  public CompletableFuture<Tuple<Long, Long>> restore(
      MemberId server, long snapshotId, int numChunks) {
    this.numChunks = numChunks;
    final CompletableFuture<Tuple<Long, Long>> result = new CompletableFuture<>();
    restoreInternal(server, snapshotId, 0, result);
    return result;
  }

  private void restoreInternal(
      MemberId server, long snapshotId, int chunkIdx, CompletableFuture<Tuple<Long, Long>> future) {
    final DefaultSnapshotRestoreRequest request =
        new DefaultSnapshotRestoreRequest(snapshotId, chunkIdx);
    client
        .requestSnapshotChunk(server, request)
        .whenCompleteAsync(
            (r, e) -> {
              if (e != null) {
                failReplication(snapshotId, future, e);
                return;
              } else if (!r.isSuccess()) {
                failReplication(
                    snapshotId,
                    future,
                    new RuntimeException(
                        String.format(
                            "Could not restore snapshot %d. Received an invalid response for request %d from server %s",
                            snapshotId, request.getChunkIdx(), server.id())));
                return;
              } else if (!snapshotConsumer.consumeSnapshotChunk(r.getSnapshotChunk())) {
                failReplication(
                    snapshotId,
                    future,
                    new RuntimeException(
                        String.format(
                            "Could not restore snapshot %d. Failed to consume snapshot chunk %d",
                            snapshotId, request.getChunkIdx())));
                return;
              }

              if (chunkIdx + 1 < numChunks) {
                restoreInternal(server, snapshotId, chunkIdx + 1, future);
                return;
              }

              if (snapshotConsumer.completeSnapshot(snapshotId)) {
                final Tuple positions = restoreContext.getSnapshotPositionSupplier().get();
                future.complete(positions);
              } else {
                failReplication(
                    snapshotId,
                    future,
                    new RuntimeException(
                        String.format(
                            "Could not restore snapshot %d. Failed to move valid snapshot.",
                            snapshotId)));
              }
            },
            executor);
  }

  private void failReplication(long snapshotId, CompletableFuture future, Throwable error) {
    future.completeExceptionally(error);
    logger.debug("Snapshot restore failed {}", snapshotId, error);
    snapshotConsumer.invalidateSnapshot(snapshotId);
  }
}
