package com.hubspot.blazar.queue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.name.Named;
import com.hubspot.blazar.data.dao.QueueItemDao;
import com.hubspot.blazar.data.queue.QueueItem;
import com.hubspot.blazar.externalservice.BuildClusterHealthChecker;
import com.hubspot.blazar.github.GitHubProtos;
import com.hubspot.blazar.util.ManagedScheduledExecutorServiceProvider;

import io.dropwizard.lifecycle.Managed;

@Singleton
public class QueueProcessor implements LeaderLatchListener, Managed, Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(QueueProcessor.class);
  private static final List<Class> PROCESSED_EVENTS_WHEN_CLUSTERS_DOWN  = ImmutableList.of(
      GitHubProtos.PushEvent.class, GitHubProtos.DeleteEvent.class, GitHubProtos.CreateEvent.class);


  private final ScheduledExecutorService executorService;
  private final Map<String, ScheduledExecutorService> queueExecutors;
  private final QueueItemDao queueItemDao;
  private final SqlEventBus eventBus;
  private final Set<Object> erroredItems;
  private final Set<QueueItem> processingItems;
  private final AtomicBoolean running;
  private final AtomicBoolean leader;
  private final BuildClusterHealthChecker buildClusterHealthChecker;
  private Optional<ScheduledFuture<?>> processingTask;

  @Inject
  public QueueProcessor(@Named("QueueProcessor") ScheduledExecutorService executorService,
                        QueueItemDao queueItemDao,
                        SqlEventBus eventBus,
                        BuildClusterHealthChecker buildClusterHealthChecker,
                        Set<Object> erroredItems) {
    this.executorService = executorService;
    this.queueExecutors = new ConcurrentHashMap<>();
    this.queueItemDao = queueItemDao;
    this.eventBus = eventBus;
    this.erroredItems = erroredItems;
    this.processingItems = Sets.newConcurrentHashSet();
    this.buildClusterHealthChecker = buildClusterHealthChecker;

    this.running = new AtomicBoolean();
    this.leader = new AtomicBoolean();
    this.processingTask = Optional.absent();
  }

  @Override
  public void start() {
    startProcessorWithCustomPollingRate(1, TimeUnit.SECONDS);
  }

  public void startProcessorWithCustomPollingRate(long delay, TimeUnit timeUnit) {
    running.set(true);
    LOG.info("Starting Queue Processor with delay of {} {}", delay, timeUnit);
    processingTask = Optional.of(executorService.scheduleAtFixedRate(this, 0, delay, timeUnit));
    LOG.info("Queue Processor Started");
  }

  @Override
  public void stop() {
    if (processingTask.isPresent()) {
      running.set(false);
      // gracefully allow processing to stop.
      boolean success = processingTask.get().cancel(false);
      if (!success && (processingTask.get().isCancelled() || processingTask.get().isDone())) {
        RuntimeException scheduledExecutorShutdownError = new RuntimeException("Failed to successfully shut down scheduled queue polling task");
        LOG.error("Error stopping QueueProcessor", scheduledExecutorShutdownError);
      }
    }
    LOG.info("Queue Processor Stopped");
  }

  @Override
  public void isLeader() {
    LOG.info("Now the leader, starting queue processing");
    leader.set(true);
  }

  @Override
  public void notLeader() {
    LOG.info("Not the leader, stopping queue processing");
    leader.set(false);
  }

  @Override
  public void run() {
    try {
      if (running.get() && leader.get()) {
        List<QueueItem> queueItemsSorted = sort(queueItemDao.getItemsReadyToExecute());
        LOG.debug("{} events found in db", queueItemsSorted.size());

        if (processingItems.size() > 0) {
          LOG.debug("{} events of those in db are in the following event thread pools (ONLY one event in each thread pool is currently running and the other are waiting)):", processingItems.size());
          printItemsInProcessingQueues();
        } else {
          LOG.debug("No events exist in the event processing thread pools", queueItemsSorted.size(), processingItems.size());
        }

        queueItemsSorted.removeAll(processingItems);
        if (queueItemsSorted.size() > 0) {
          LOG.debug("Will schedule {} events for processing (i.e. those found in db minus those already processing)", queueItemsSorted.size());
        }

        processingItems.addAll(queueItemsSorted);


        for (QueueItem queuedItem : queueItemsSorted) {
          String eventType = queuedItem.getType().getSimpleName();
          LOG.debug("Processing event {}: eventId:{}", eventType, queuedItem.getId().get());

          if (!canDequeueEvent(queuedItem)) {
            LOG.warn("Will not schedule event {}(id: {}) for processing because there is no healthy cluster available at the moment (only git push events are dequeued when all build clusters are down)",
                eventType, queuedItem.getId().get());
            processingItems.remove(queuedItem);
            return;
          }

          queueExecutors.computeIfAbsent(eventType, k -> {
            return new ManagedScheduledExecutorServiceProvider(1, "QueueProcessor-" + eventType).get();
          }).execute(new ProcessItemRunnable(queuedItem));
        }
      }
    } catch (Throwable t) {
      LOG.error("An error occurred while scheduling events in the queue for processing.", t);
    }
  }

  private List<QueueItem> sort(Set<QueueItem> queueItems) {
    return queueItems.stream()
        .sorted(Comparator.comparing(item -> item.getId().get()))
        .collect(Collectors.toList());
  }

  private boolean canDequeueEvent(QueueItem queueItem) {
    return buildClusterHealthChecker.isSomeClusterAvailable() ||
        (!buildClusterHealthChecker.isSomeClusterAvailable() &&
            PROCESSED_EVENTS_WHEN_CLUSTERS_DOWN.contains(queueItem.getType()));
  }

  private class ProcessItemRunnable implements Runnable {
    private final QueueItem queueItem;

    public ProcessItemRunnable(QueueItem queueItem) {
      this.queueItem = queueItem;
    }

    @Override
    public void run() {
      Stopwatch timer = Stopwatch.createStarted();
      String eventName = queueItem.getType().getSimpleName();
      try {
        if (!queueItemDao.isItemStillQueued(queueItem)) {
          LOG.info("Queued event {}(id: {}) was already completed, will not schedule for processing", eventName, queueItem.getId().get());
        } else if (process(queueItem.getItem())) {
          LOG.debug("Queued event {}(id: {}) was successfully processed, will be marked as completed", eventName, queueItem.getId().get());
          checkResult(queueItemDao.complete(queueItem), queueItem);
        } else if (queueItem.getRetryCount() < 9) {
          LOG.warn("Queued event {}(id: {}) failed to process, will increase its retry counter and will leave it in the queue to be scheduled for processing in the next cycle", eventName, queueItem.getId().get());
          checkResult(queueItemDao.increaseRetryCounter(queueItem), queueItem);
        } else {
          LOG.warn("Queued event {}(id: {}) failed to process 10 times, will be marked as completed and will not be retried", eventName, queueItem.getId().get());
          checkResult(queueItemDao.complete(queueItem), queueItem);
        }
      } catch (Throwable t) {
        LOG.error("Unexpected error while processing queued event: {}(id: {})", eventName, queueItem.getId().get(), t);
      } finally {
        processingItems.remove(queueItem);
        timer.stop();
        LOG.debug("Processing of event {}(id: {}) took {}ms", eventName, queueItem.getId().get(), timer.elapsed(TimeUnit.MILLISECONDS));
      }
    }

    private boolean process(Object event) {
      eventBus.dispatch(event);

      return !erroredItems.remove(event);
    }

    private void checkResult(int result, QueueItem queueItem) {
      if (result != 1) {
        LOG.warn("Could not find queue item with id {} to update", queueItem.getId().get());
      }
    }
  }

  private void printItemsInProcessingQueues() {
    queueExecutors.forEach((eventClass, scheduledExecutorService) -> {
      LOG.debug("Event processing thread pool:'{}' has {} events queued and 1 processing", eventClass, ((ScheduledThreadPoolExecutor)scheduledExecutorService).getQueue().size());
    });
  }
}
