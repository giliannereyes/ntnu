package com.giliannereyes;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Workers {
  private final Queue<Runnable> tasks;
  private final List<Thread> threads;
  private final AtomicBoolean stopped;
  private final int numThreads;
  private final ReentrantLock lock;
  private final Condition taskAvailable;

  public Workers(int numThreads) {
    this.numThreads = numThreads;
    this.tasks = new LinkedList<>();
    this.threads = new ArrayList<>();
    this.stopped = new AtomicBoolean(false);
    this.lock = new ReentrantLock();
    this.taskAvailable = lock.newCondition();
  }

  public void start() {
    for (int i = 0; i < numThreads; i++) {
      Thread thread = new Thread(() -> {
        while (true) {
          Runnable task;
          lock.lock();
          try {
            while (tasks.isEmpty() && !stopped.get()) {
              taskAvailable.await();
            }
            if (stopped.get() && tasks.isEmpty()) {
              break;
            }
            task = tasks.poll();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          } finally {
            lock.unlock();
          }
          if (task != null) {
            task.run();
          }
        }
      });
      threads.add(thread);
      thread.start();
    }
  }

  public void post(Runnable task) {
    lock.lock();
    try {
      tasks.add(task);
      taskAvailable.signal();
    } finally {
      lock.unlock();
    }
  }

  public void postTimeout(Runnable task, long delayMs) {
    new Thread(() -> {
      try {
        Thread.sleep(delayMs);
        post(task);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }).start();
  }

  public void stop() {
    stopped.set(true);
    lock.lock();
    try {
      taskAvailable.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public void join() throws InterruptedException {
    for (Thread thread : threads) {
      thread.join();
    }
  }
}
