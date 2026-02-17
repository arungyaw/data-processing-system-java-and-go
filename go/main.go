package main

import (
	"bufio"
	"fmt"
	"log"
	"math/rand"
	"os"
	"sync"
	"time"
)

// Task represents a single unit of work in the system.
// Keeping it immutable-by-convention (no in-place mutation after creation)
// reduces concurrency complexity and makes task handling predictable.
type Task struct {
	ID      int
	Payload string
}

// worker pulls tasks from the tasks channel, simulates processing, and sends
// formatted results to resultsChan.
//
// Concurrency model (Go-idiomatic):
// - Channels provide safe synchronization for task distribution.
// - Workers terminate naturally when the tasks channel is closed and drained.
// - No locks are required for task queue access because channels are concurrency-safe.
//
// Error handling:
//   - In Go, errors are explicit return values. This worker function does not
//     directly perform I/O, so it does not return an error. File I/O is handled
//     centrally by a dedicated writer goroutine.
func worker(workerID int, tasks <-chan Task, resultsChan chan<- string, wg *sync.WaitGroup) {
	defer wg.Done()

	// Local RNG per worker avoids global state and deprecation warnings
	// related to rand.Seed in newer Go versions.
	// This also avoids any contention between goroutines over shared RNG state.
	r := rand.New(rand.NewSource(time.Now().UnixNano() + int64(workerID)))

	log.Printf("Worker-%d STARTED", workerID)

	for task := range tasks {
		log.Printf("Worker-%d Picked Task-%d", workerID, task.ID)

		// Simulate compute delay (randomized to make concurrency visible in logs).
		time.Sleep(time.Duration(r.Intn(300)+150) * time.Millisecond)

		// Prepare output line (mirrors Java behavior for cross-language comparison).
		resultLine := fmt.Sprintf("[%s] Worker-%d processed Task-%d payload='%s'\n",
			time.Now().Format(time.RFC3339Nano),
			workerID,
			task.ID,
			task.Payload,
		)

		// Send result to the writer goroutine. This separates compute from I/O,
		// and avoids multiple goroutines writing to the file concurrently.
		resultsChan <- resultLine

		log.Printf("Worker-%d Completed Task-%d", workerID, task.ID)
	}

	log.Printf("Worker-%d FINISHED", workerID)
}

// writer is the sole owner of the output file resource.
// Only this goroutine writes to disk, which guarantees:
// - no interleaved writes
// - no need for mutex locks around file output
//
// Error handling:
// - File creation/write/flush/close errors are logged.
// - If file creation fails, we drain resultsChan to prevent worker deadlock.
func writer(outputPath string, resultsChan <-chan string, done chan<- struct{}) {
	defer close(done)

	file, err := os.Create(outputPath)
	if err != nil {
		log.Printf("ERROR: failed to create output file '%s': %v", outputPath, err)

		// Drain resultsChan to ensure workers never block forever on send.
		for range resultsChan {
		}
		return
	}
	defer func() {
		if cerr := file.Close(); cerr != nil {
			log.Printf("ERROR: failed to close output file: %v", cerr)
		}
	}()

	buf := bufio.NewWriter(file)
	defer func() {
		if ferr := buf.Flush(); ferr != nil {
			log.Printf("ERROR: failed to flush output buffer: %v", ferr)
		}
	}()

	for line := range resultsChan {
		if _, werr := buf.WriteString(line); werr != nil {
			log.Printf("ERROR: failed to write output line: %v", werr)
			// Continue draining to avoid deadlock; output may be partial.
			continue
		}
	}
}

func main() {
	// Parameters aligned with Java for direct comparison.
	numWorkers := 4
	numTasks := 20

	// tasks acts as a concurrency-safe queue.
	// Buffering to numTasks allows the producer to enqueue all tasks without blocking.
	tasks := make(chan Task, numTasks)

	// resultsChan decouples compute from disk I/O.
	// Buffering prevents workers from blocking on every single write.
	resultsChan := make(chan string, numTasks)

	// done is closed by writer when the output file is fully flushed and closed.
	done := make(chan struct{})

	// Store output in a predictable build artifact directory.
	_ = os.MkdirAll("target", 0755)
	outputPath := "target/go-output.txt"

	log.Println("Go system starting...")
	log.Printf("Workers: %d", numWorkers)
	log.Printf("Tasks loaded: %d", numTasks)
	log.Printf("Writing output to: %s", outputPath)

	// Start the dedicated writer goroutine (owns the shared output resource).
	go writer(outputPath, resultsChan, done)

	// Start worker goroutines.
	var wg sync.WaitGroup
	wg.Add(numWorkers)
	for w := 1; w <= numWorkers; w++ {
		go worker(w, tasks, resultsChan, &wg)
	}

	// Produce tasks.
	for i := 1; i <= numTasks; i++ {
		tasks <- Task{ID: i, Payload: fmt.Sprintf("data-%d", i)}
	}

	// Close tasks channel to signal that no more tasks will be added.
	// Workers will finish naturally after draining the channel.
	close(tasks)

	// Wait until all workers have completed processing.
	wg.Wait()

	// Close results channel to signal writer to finish.
	close(resultsChan)

	// Wait for writer to flush and close file.
	<-done

	log.Println("Go system ended.")
}
