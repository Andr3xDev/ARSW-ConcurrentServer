package edu.arsw.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A simple concurrent web server that listens for incoming client connections
 * and handles each request in a separate thread from a fixed thread pool.
 * This server serves files from a specified web root directory.
 */
public class WebServer {

    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 10;
    private static final String WEB_ROOT = "resources";

    /**
     * The main entry point for the web server application.
     * Initializes the server socket, sets up the thread pool, and starts
     * listening for client connections.
     */
    public static void main(String[] args) {
        // Create a fixed-size thread pool to manage concurrent client requests
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Try-with-resources ensures the ServerSocket is closed automatically.
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Web server started on port " + PORT);
            System.out.println("Serving files from: " + WEB_ROOT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New incoming connection from: " + clientSocket.getInetAddress());
                executorService.submit(new RequestHandler(clientSocket, WEB_ROOT));
            }
        } catch (IOException e) {
            System.err.println("Error starting or running the server: " + e.getMessage());
            executorService.shutdown();
        } finally {
            executorService.shutdown();
            System.out.println("Web server stopped.");
        }
    }
}