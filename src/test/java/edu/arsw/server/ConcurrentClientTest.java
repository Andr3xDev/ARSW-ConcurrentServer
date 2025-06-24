package edu.arsw.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A concurrent client test application designed to stress-test the web server.
 * It simulates multiple clients sending concurrent HTTP requests to the server
 * and reports on the success or failure of these requests.
 */
public class ConcurrentClientTest {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final int NUMBER_OF_CONCURRENT_REQUESTS = 100;
    private static final String[] RESOURCES = { "/", "/styles.css", "/image.jpg", "/index.html" };
    private static AtomicInteger successfulRequests = new AtomicInteger(0);
    private static AtomicInteger failedRequests = new AtomicInteger(0);

    /**
     * The main method to run the concurrent client test.
     * It sets up a thread pool, dispatches multiple request tasks,
     * waits for their completion, and reports the aggregated results.
     *
     * @throws InterruptedException If the current thread is interrupted while
     *                              waiting for tasks to complete.
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting concurrent tests for the web server...");
        System.out.println("Sending " + NUMBER_OF_CONCURRENT_REQUESTS + " requests to " + HOST + ":" + PORT);

        ExecutorService executor = Executors.newFixedThreadPool(NUMBER_OF_CONCURRENT_REQUESTS);

        long startTime = System.currentTimeMillis();

        // Loop to submit multiple request tasks to the executor service.
        for (int i = 0; i < NUMBER_OF_CONCURRENT_REQUESTS; i++) {
            final int requestId = i;
            final String resourceToRequest = RESOURCES[requestId % RESOURCES.length];

            executor.submit(() -> {
                try (Socket socket = new Socket(HOST, PORT);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String request = "GET " + resourceToRequest + " HTTP/1.1\r\nHost: " + HOST + ":" + PORT
                            + "\r\nConnection: close\r\n\r\n";
                    socket.getOutputStream().write(request.getBytes());
                    socket.getOutputStream().flush();

                    String responseLine = in.readLine();
                    if (responseLine != null && responseLine.contains("200 OK")) {
                        successfulRequests.incrementAndGet();
                    } else {
                        failedRequests.incrementAndGet();
                        System.err.println("Request " + requestId + " (" + resourceToRequest + ") failed: "
                                + (responseLine != null ? responseLine : "Empty response"));
                    }
                } catch (IOException e) {
                    failedRequests.incrementAndGet();
                    System.err.println("Connection/I/O error for request " + requestId + " (" + resourceToRequest
                            + "): " + e.getMessage());
                }
            });
        }

        // Shut down the executor service to prevent new tasks from being submitted.
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("\n--- Concurrent Test Results ---");
        System.out.println("Total requests sent: " + NUMBER_OF_CONCURRENT_REQUESTS);
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Failed requests: " + failedRequests.get());
        System.out.println("Total execution time: " + duration + " ms");

        if (failedRequests.get() == 0) {
            System.out.println(
                    "All requests were successful! The server appears to handle concurrency well.");
        } else {
            System.out.println("Warning: Some requests failed. Please check the error logs for details.");
        }
    }
}