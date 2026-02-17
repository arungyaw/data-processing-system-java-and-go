package com.example.dataproc;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Worker is the unit executed by the thread pool (ExecutorService).
 *
 * Each Worker:
 * 1) Pulls tasks from the shared queue.
 * 2) Simulates "processing" via Thread.sleep(...)
 * 3) Writes output lines to a shared output file.
 *
 * Concurrency concerns addressed:
 * - Queue access is protected inside SharedTaskQueue using a lock.
 * - File writes are protected using a separate lock to prevent interleaving.
 *
 * Error handling:
 * - InterruptedException: restore interrupt status and exit the loop.
 * - IOException: log and stop (file output is a core requirement; continuing
 * would be misleading).
 */
public class Worker implements Runnable {

    private final int workerId;
    private final SharedTaskQueue taskQueue;

    // Shared output resource: one file writer used by all workers.
    private final BufferedWriter writer;

    // Separate lock for file writes, to keep output lines atomic.
    private final ReentrantLock fileLock;

    public Worker(int workerId,
            SharedTaskQueue taskQueue,
            BufferedWriter writer,
            ReentrantLock fileLock) {
        this.workerId = workerId;
        this.taskQueue = taskQueue;
        this.writer = writer;
        this.fileLock = fileLock;
    }

    @Override
    public void run() {
        log("STARTED");

        // Loop until the queue is empty or this thread is interrupted.
        while (!Thread.currentThread().isInterrupted()) {
            Optional<Task> taskOpt;

            // Queue retrieval is concurrency-safe by design (SharedTaskQueue locks
            // internally).
            try {
                taskOpt = taskQueue.getTask();
            } catch (Exception e) {
                // Defensive catch: retrieval should not normally fail,
                // but logging preserves diagnosability under unexpected runtime issues.
                logError("Unexpected error while retrieving task", e);
                break;
            }

            // If no tasks remain, terminate gracefully.
            if (taskOpt.isEmpty()) {
                break;
            }

            Task task = taskOpt.get();
            log("Picked Task-" + task.getId());

            try {
                // Simulate CPU-bound work or I/O latency.
                // Random delay makes concurrent behavior visible in logs.
                Thread.sleep(ThreadLocalRandom.current().nextInt(150, 450));

                String resultLine = String.format(
                        "[%s] Worker-%d processed Task-%d payload='%s'%n",
                        Instant.now(), workerId, task.getId(), task.getPayload());

                // File writing is a shared critical section.
                // Without a lock, output lines could interleave and become unreadable.
                fileLock.lock();
                try {
                    writer.write(resultLine);
                    writer.flush();
                } finally {
                    fileLock.unlock();
                }

                log("Completed Task-" + task.getId());

            } catch (InterruptedException e) {
                // InterruptedException is expected when shutting down.
                // Restore interrupt status and terminate to avoid ignoring cancellation.
                logError("Interrupted while processing Task-" + task.getId(), e);
                Thread.currentThread().interrupt();
                break;

            } catch (IOException e) {
                // File errors are handled explicitly per requirements.
                logError("I/O error while writing output for Task-" + task.getId(), e);
                break;

            } catch (Exception e) {
                // Defensive catch-all ensures the worker does not silently die.
                logError("Unexpected runtime error for Task-" + task.getId(), e);
                break;
            }
        }

        log("FINISHED");
    }

    private void log(String msg) {
        System.out.printf("Worker-%d %s%n", workerId, msg);
    }

    private void logError(String msg, Exception e) {
        System.err.printf("Worker-%d ERROR: %s | %s%n", workerId, msg, e.getMessage());
    }
}
