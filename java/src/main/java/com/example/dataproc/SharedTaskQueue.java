package com.example.dataproc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A concurrency-safe task queue shared across worker threads.
 *
 * Design choice:
 * - The queue uses an explicit ReentrantLock instead of "synchronized"
 * so the locking intent is visible and extensible (e.g., tryLock, fairness).
 *
 * Behavior:
 * - addTask() appends tasks.
 * - getTask() removes and returns the next task.
 * - If the queue is empty, getTask() returns Optional.empty() rather than
 * throwing.
 * This avoids "empty queue access" exceptions and allows workers to terminate
 * cleanly.
 */
public class SharedTaskQueue {

    private final Deque<Task> queue = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Adds a task to the back of the queue.
     * This is a critical section because multiple threads can add tasks
     * concurrently.
     */
    public void addTask(Task task) {
        lock.lock();
        try {
            queue.addLast(task);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the next task from the queue.
     * Returning Optional avoids null checks scattered across workers.
     */
    public Optional<Task> getTask() {
        lock.lock();
        try {
            return Optional.ofNullable(queue.pollFirst());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns current size. Useful for startup logging/debugging.
     * This is also synchronized to avoid inconsistent reads.
     */
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }
}
