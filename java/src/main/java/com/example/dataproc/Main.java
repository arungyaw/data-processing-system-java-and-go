package com.example.dataproc;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Entry point for the Data Processing System.
 *
 * Key lifecycle rule:
 * - The shared output writer MUST remain open until all worker threads
 * complete.
 * Otherwise, workers will throw "Stream closed" when attempting to write.
 */
public class Main {

    public static void main(String[] args) {
        final int numWorkers = 4;
        final int numTasks = 20;

        SharedTaskQueue queue = new SharedTaskQueue();
        for (int i = 1; i <= numTasks; i++) {
            queue.addTask(new Task(i, "data-" + i));
        }

        ReentrantLock fileLock = new ReentrantLock();
        ExecutorService pool = Executors.newFixedThreadPool(numWorkers);

        System.out.println("Java system starting...");
        System.out.println("Workers: " + numWorkers);
        System.out.println("Tasks loaded: " + queue.size());

        BufferedWriter writer = null;

        try {
            // Keep the writer open for the entire duration of worker execution.
            // Writing to "target/" also avoids permission issues and keeps build artifacts
            // organized.
            writer = new BufferedWriter(new FileWriter("target/java-output.txt", false));

            for (int w = 1; w <= numWorkers; w++) {
                pool.submit(new Worker(w, queue, writer, fileLock));
            }

            // Stop accepting new tasks and wait for completion.
            pool.shutdown();

            // Wait for workers to finish. If they hang, force shutdown.
            if (!pool.awaitTermination(20, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }

        } catch (IOException e) {
            System.err.println("Failed to open output file: " + e.getMessage());
            pool.shutdownNow();

        } catch (InterruptedException e) {
            // If the main thread is interrupted, attempt a clean shutdown and preserve
            // interrupt flag.
            pool.shutdownNow();
            Thread.currentThread().interrupt();

        } finally {
            // Close the writer only after workers have terminated (or shutdownNow has been
            // called).
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    System.err.println("Failed to close output file cleanly: " + e.getMessage());
                }
            }
            System.out.println("Output written to: target/java-output.txt");
            System.out.println("Java system ended.");
        }
    }
}
