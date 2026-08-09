package com.giliannereyes;

public class Main {
  public static void main(String[] args) throws InterruptedException {
    Workers workerThreads = new Workers(4);
    Workers eventLoop = new Workers(1);

    workerThreads.start();
    eventLoop.start();

    workerThreads.post(() -> System.out.println("Task A"));
    workerThreads.post(() -> System.out.println("Task B"));

    eventLoop.post(() -> System.out.println("Task C"));
    eventLoop.post(() -> System.out.println("Task D"));

    workerThreads.stop();
    eventLoop.stop();

    workerThreads.join();
    eventLoop.join();
  }
}
