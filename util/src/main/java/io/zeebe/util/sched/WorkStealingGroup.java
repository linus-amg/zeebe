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

import static io.zeebe.util.sched.ActorTask.TaskSchedulingState.QUEUED;

import java.util.concurrent.ThreadLocalRandom;

/** Workstealing group maintains a queue per thread. */
public class WorkStealingGroup {
  private final int numOfThreads;
  private final ActorTaskQueue[] taskQueues;

  public WorkStealingGroup(int numOfThreads) {
    this.numOfThreads = numOfThreads;
    this.taskQueues = new ActorTaskQueue[numOfThreads];
    for (int i = 0; i < numOfThreads; i++) {
      taskQueues[i] = new ActorTaskQueue();
    }
  }

  /**
   * Submit the task into the provided thread's queue
   *
   * @param task the task to submit
   * @param threadId the id of the thread into which queue the task should be submitted
   */
  public void submit(ActorTask task, int threadId) {
    task.schedulingState = QUEUED;
    taskQueues[threadId].append(task);
  }

  /**
   * Attempts to acquire the next task to execute
   *
   * @return the acquired task or null if no task is available
   */
  protected ActorTask getNextTask() {
    final ActorThread currentThread = ActorThread.current();
    ActorTask nextTask = taskQueues[currentThread.getRunnerId()].pop();

    if (nextTask == null) {
      nextTask = trySteal(currentThread);
    }

    return nextTask;
  }

  /**
   * Work stealing: when this runner (aka. the "thief") has no more tasks to run, it attempts to
   * take ("steal") a task from another runner (aka. the "victim").
   *
   * <p>Work stealing is a mechanism for <em>load balancing</em>: it relies upon the assumption that
   * there is more work to do than there is resources (threads) to run it.
   */
  private ActorTask trySteal(ActorThread currentThread) {
    /*
     * This implementation uses a random offset into the runner array. The idea is to
     *
     * a) reduce probability for contention in situations where we have multiple runners
     *    (threads) trying to steal work at the same time: if they all started at the same
     *    offset, they would all look at the same runner as potential victim and contend
     *    on it's job queue
     *
     * b) to make sure a runner does not always look at the same other runner first and by
     *    this potentially increase the probability to find work on the first attempt
     *
     * However, the calculation of the random and the handling also needs additional compute time.
     * Experimental verification of the effectiveness of the optimization has not been conducted yet.
     * Also, the optimization only makes sense if the system uses at least 3 runners.
     */
    final int offset = ThreadLocalRandom.current().nextInt(numOfThreads);

    for (int i = offset; i < offset + numOfThreads; i++) {
      final int runnerId = i % numOfThreads;

      if (runnerId != currentThread.getRunnerId()) {
        final ActorTask stolenActor = taskQueues[runnerId].trySteal();

        if (stolenActor != null) {
          return stolenActor;
        }
      }
    }

    return null;
  }
}
