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
package io.zeebe.util.sched;

import io.zeebe.util.sched.ActorScheduler.ActorSchedulerBuilder;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Used to submit {@link ActorTask ActorTasks} and Blocking Actions to the scheduler's internal
 * runners and queues.
 */
public class ActorExecutor {
  private final ActorThreadGroup cpuBoundThreads;
  private final ActorThreadGroup ioBoundThreads;
  private final ThreadPoolExecutor blockingTasksRunner;
  private Duration blockingTasksShutdownTime;

  public ActorExecutor(ActorSchedulerBuilder builder) {
    this.ioBoundThreads = builder.getIoBoundActorThreads();
    this.cpuBoundThreads = builder.getCpuBoundActorThreads();
    this.blockingTasksRunner = builder.getBlockingTasksRunner();
    this.blockingTasksShutdownTime = builder.getBlockingTasksShutdownTime();
  }

  /**
   * Initially submit a non-blocking actor to be managed by this scheduler.
   *
   * @param task the task to submit
   */
  public ActorFuture<Void> submitCpuBound(ActorTask task) {
    return submitTask(task, cpuBoundThreads);
  }

  public ActorFuture<Void> submitIoBoundTask(ActorTask task) {
    return submitTask(task, ioBoundThreads);
  }

  private ActorFuture<Void> submitTask(ActorTask task, ActorThreadGroup threadGroup) {
    final ActorFuture<Void> startingFuture = task.onTaskScheduled(this, threadGroup);

    threadGroup.submit(task);
    return startingFuture;
  }

  /**
   * Sumbit a blocking action to run using the scheduler's blocking thread pool
   *
   * @param action the action to submit
   */
  public void submitBlocking(Runnable action) {
    blockingTasksRunner.execute(action);
  }

  public void start() {
    cpuBoundThreads.start();
    ioBoundThreads.start();
  }

  public CompletableFuture<Void> closeAsync() {
    blockingTasksRunner.shutdown();

    final CompletableFuture<Void> resultFuture =
        CompletableFuture.allOf(ioBoundThreads.closeAsync(), cpuBoundThreads.closeAsync());

    try {
      blockingTasksRunner.awaitTermination(
          blockingTasksShutdownTime.getSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    return resultFuture;
  }

  public ActorThreadGroup getCpuBoundThreads() {
    return cpuBoundThreads;
  }

  public ActorThreadGroup getIoBoundThreads() {
    return ioBoundThreads;
  }

  public Duration getBlockingTasksShutdownTime() {
    return blockingTasksShutdownTime;
  }

  public void setBlockingTasksShutdownTime(Duration duration) {
    blockingTasksShutdownTime = duration;
  }
}
