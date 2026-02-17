# Java Data Processing System

This Java program implements a **multi-threaded Data Processing System** where multiple worker threads process tasks in parallel. Workers retrieve tasks from a **shared queue**, simulate computation with a delay, and write results to a **shared output resource** (an output file). The implementation is designed to avoid race conditions, prevent deadlocks, and terminate cleanly after all tasks complete.

---

## Requirements
- **Java:** 11+
- **Maven:** 3.8+

---

## Project Structure
```
java/
  pom.xml
  src/main/java/com/example/dataproc/
    Main.java
    Task.java
    SharedTaskQueue.java
    Worker.java
  target/
    java-output.txt   (generated)
```

---

## How to Run (Step-by-Step)

### Option A: Run with Maven (recommended)
From the repository root:
```bash
cd java
mvn -q clean compile exec:java
```

### Compile + Run without exec plugin
```bash
cd java
mvn -q clean compile
java -cp target/classes com.example.dataproc.Main
```

---

## What the Program Does (Execution Flow)

1. **Main** creates a fixed set of tasks (Task-1 … Task-N).
2. Tasks are inserted into a **SharedTaskQueue** (shared resource).
3. A fixed-size **thread pool** is created using `ExecutorService`.
4. Each **Worker** repeatedly:
   - retrieves a task from the shared queue,
   - simulates work (`Thread.sleep(...)`),
   - writes a formatted result line into the shared output file.
5. When the queue is empty, workers exit cleanly.
6. The application shuts down the thread pool and closes file resources safely.

---

## Concurrency Design (How Concurrency Is Implemented)

### Shared Resource: Task Queue
- Implemented as `SharedTaskQueue` built on `ArrayDeque<Task>`.
- Protected with a **ReentrantLock** so that queue operations are atomic.
- `getTask()` returns `Optional<Task>` and returns empty when no tasks remain (avoids unsafe empty access).

**Why this prevents race conditions:**
- Only one thread at a time can remove a task from the queue, ensuring each task is processed once.

### Worker Threads and Thread Management
- Workers are run using a fixed-size `ExecutorService`.
- This ensures a controlled number of threads execute concurrently (no uncontrolled thread creation).

### Shared Resource: Output File
- Workers share a single `BufferedWriter`.
- File writes are guarded by a second **ReentrantLock** (fileLock) so output lines do not interleave.

**Why this avoids corruption:**
- Each write/flush block becomes a critical section, guaranteeing line-level output integrity.

### Deadlock Avoidance
- Locks are held only for short, well-defined critical sections.
- Queue lock and file lock are not held together for long durations.
- Workers retrieve the task first, then do processing, then write output (no nested long lock hold).

### Safe Termination
- Workers stop when `getTask()` returns empty.
- `ExecutorService.shutdown()` + `awaitTermination()` ensures the program waits for completion.
- Interrupts are handled by restoring interrupt status and exiting.

---

## Exception Handling (How Errors Are Managed)

### Empty Queue / No More Work
- Not treated as an exception.
- `Optional.empty()` signals termination without throwing.

### InterruptedException
- Occurs if a thread is interrupted (e.g., shutdownNow).
- Worker catches it, logs the error, restores interrupt flag (`Thread.currentThread().interrupt()`), and exits.

### IOException
- Covers file I/O problems (permissions, disk issues, stream errors).
- Worker logs and exits (file output is a core requirement; partial output is safer than silent failure).

---

## Logging (What Is Logged)
Console logs include:
- `Worker-X STARTED`
- `Worker-X Picked Task-Y`
- `Worker-X Completed Task-Y`
- `Worker-X FINISHED`
- `ERROR` messages for exceptions


---

## Output
- Output file is written to:
  - `java/target/java-output.txt`
- Each line represents one processed task, including timestamp, worker id, task id, and payload.

---

## License
Academic use only.

---
