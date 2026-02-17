# Go Data Processing System 

This Go program implements a **concurrent Data Processing System** using goroutines and channels. Multiple workers process tasks in parallel by receiving tasks from a channel (queue), simulating computation, and sending results to a dedicated writer goroutine that owns the output file.

---

## Requirements
- **Go:** 1.20+ (recommended)

---

## Project Structure
```
go/
  go.mod
  main.go
  target/
    go-output.txt   (generated)
```

---

## Setup (First-Time)
From the repository root:
```bash
cd go
go mod init dataproc
```

If `go.mod` already exists, you can skip this step.

---

## How to Run (Step-by-Step)
From the repository root:
```bash
cd go
go run .
```

---

## What the Program Does (Execution Flow)

1. `main()` creates:
   - a buffered `tasks` channel containing Task-1 … Task-N
   - a buffered `resultsChan` channel for output lines
2. A **writer goroutine** starts first and opens the output file.
3. Multiple **worker goroutines** start and process tasks concurrently:
   - receive from `tasks`
   - simulate compute delay
   - send formatted result lines to `resultsChan`
4. `close(tasks)` signals no more tasks will be produced.
5. `WaitGroup` waits for all workers to finish.
6. `close(resultsChan)` signals the writer to finish and flush.
7. A `done` channel confirms the writer has closed the file.
8. Program exits cleanly after all work is complete.

---

## Concurrency Design (How Concurrency Is Implemented)

### Shared Resource: Task Queue (Channel)
- The task queue is implemented as a **buffered channel**:
  - `tasks := make(chan Task, numTasks)`

**Why this prevents race conditions:**
- Channels are concurrency-safe. Multiple goroutines can receive from the same channel without explicit locks.
- Each task is delivered to exactly one worker (no duplication).

### Worker Goroutines
- Each worker runs as a goroutine and loops:
  - `for task := range tasks { ... }`

**Termination behavior:**
- When `tasks` is closed and drained, the loop ends automatically.

### Shared Resource: Output File (Writer Goroutine Pattern)
- Only one goroutine writes to disk (the writer).
- Workers **never** write to the file directly.
- Workers send strings to `resultsChan`, and the writer drains that channel and writes sequentially.

**Why this avoids interleaving without mutex:**
- The file is owned by a single goroutine, so writes cannot overlap.
- This is a standard Go pattern for safe shared I/O.

### Deadlock Avoidance
- Channels are closed in the correct order:
  - close `tasks` after producing all tasks
  - wait for workers to finish
  - close `resultsChan` so writer can exit
- Writer uses a `done` signal so `main` does not exit early.
- If file creation fails, the writer drains `resultsChan` to prevent workers from blocking on send.

### Safe Termination
- `WaitGroup` guarantees all workers finish.
- Closing `resultsChan` guarantees writer terminates.
- `done` confirms file flush/close completed.

---

## Error Handling (How Errors Are Managed)

Go uses explicit error values rather than exceptions.

### File Creation Errors
- `os.Create(...)` returns an error which is checked:
  - `if err != nil { ... }`
- If file creation fails, the writer drains `resultsChan` so workers do not block indefinitely.

### Write / Flush / Close Errors
- Each write checks the returned error.
- `defer` is used to guarantee cleanup:
  - `defer file.Close()`
  - `defer buf.Flush()`
- Errors are logged so failures are visible for grading.

---

## Logging (What Is Logged)
Console logs include:
- `Worker-X STARTED`
- `Worker-X Picked Task-Y`
- `Worker-X Completed Task-Y`
- `Worker-X FINISHED`
- `ERROR` logs for file and write failures

---

## Output
- Output file is written to:
  - `go/target/go-output.txt`
- Each line includes timestamp, worker id, task id, and payload.

---

## License
Academic Use Only

---


