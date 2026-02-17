package com.example.dataproc;

/**
 * Represents one unit of work in the system.
 *
 * In this assignment, a "task" is intentionally simple:
 * it has an identifier and a payload string that simulates input data.
 *
 * Keeping Task immutable reduces concurrency risks because worker threads
 * can safely read shared Task objects without additional synchronization.
 */
public final class Task {
    private final int id;
    private final String payload;

    public Task(int id, String payload) {
        this.id = id;
        this.payload = payload;
    }

    public int getId() {
        return id;
    }

    public String getPayload() {
        return payload;
    }
}
