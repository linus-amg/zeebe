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
package io.zeebe.client.impl.worker;

import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.client.impl.Loggers;
import io.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

public class JobWorkerImpl implements JobWorker, CloseableSilently {

  private static final Logger LOG = Loggers.JOB_WORKER_LOGGER;

  // job queue state
  private final int maxJobsActive;
  private final int activationThreshold;
  private final AtomicInteger remainingJobs;

  // job execution facilities
  private final ExecutorService executor;
  private final JobRunnableFactory jobRunnableFactory;

  // state synchronization
  private final AtomicBoolean acquiringJobs = new AtomicBoolean(true);
  private final AtomicReference<JobPoller> jobPoller;

  public JobWorkerImpl(
      int maxJobsActive,
      ScheduledExecutorService executor,
      Duration pollInterval,
      JobRunnableFactory jobRunnableFactory,
      JobPoller jobPoller) {

    this.maxJobsActive = maxJobsActive;
    this.activationThreshold = Math.round(maxJobsActive * 0.3f);
    this.remainingJobs = new AtomicInteger(0);

    this.executor = executor;
    this.jobRunnableFactory = jobRunnableFactory;

    this.jobPoller = new AtomicReference<>(jobPoller);

    executor.scheduleWithFixedDelay(
        this::tryActivateJobs, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public boolean isOpen() {
    return acquiringJobs.get();
  }

  @Override
  public boolean isClosed() {
    return !isOpen() && jobPoller.get() != null && remainingJobs.get() <= 0;
  }

  @Override
  public void close() {
    acquiringJobs.set(false);
  }

  private void tryActivateJobs() {
    final int remainingJobs = this.remainingJobs.get();
    if (shouldActivateJobs(remainingJobs)) {
      activateJobs();
    }
  }

  private void activateJobs() {
    final JobPoller jobPoller = this.jobPoller.getAndSet(null);
    if (jobPoller != null) {
      // check the condition again within the critical section
      // to avoid race conditions that would let us exceed the buffer size
      final int currentRemainingJobs = remainingJobs.get();
      if (shouldActivateJobs(currentRemainingJobs)) {
        final int maxActivatedJobs = maxJobsActive - currentRemainingJobs;
        try {
          jobPoller.poll(
              maxActivatedJobs,
              this::submitJob,
              activatedJobs -> {
                remainingJobs.addAndGet(activatedJobs);
                this.jobPoller.set(jobPoller);
              });
        } catch (Exception e) {
          LOG.warn("Failed to activate jobs", e);
          this.jobPoller.set(jobPoller);
        }
      } else {
        this.jobPoller.set(jobPoller);
      }
    }
  }

  private boolean shouldActivateJobs(int remainingJobs) {
    return acquiringJobs.get() && remainingJobs <= activationThreshold;
  }

  private void submitJob(ActivatedJob job) {
    executor.execute(jobRunnableFactory.create(job, this::jobHandlerFinished));
  }

  private void jobHandlerFinished() {
    final int remainingJobs = this.remainingJobs.decrementAndGet();
    if (shouldActivateJobs(remainingJobs)) {
      activateJobs();
    }
  }
}
