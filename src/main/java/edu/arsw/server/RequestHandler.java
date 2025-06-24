package edu.arsw.server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * Handles individual client HTTP requests within a separate thread.
 * This class implements Runnable, allowing it to be executed by an
 * ExecutorService.
 * It parses the incoming HTTP request, retrieves the requested file from the
 * web root,
 * and sends an HTTP response (including file content or an error message) back
 * to the client.
 */
public class RequestHandler implements Runnable {

    private Socket clientSocket;
    private String webRoot;

    /**
     * Constructs a new RequestHandler.
     * 
     * @param clientSocket The Socket representing the client connection.
     * @param webRoot      The root directory for serving files.
     */
    public RequestHandler(Socket clientSocket, String webRoot) {
        this.clientSocket = clientSocket;
        this.webRoot = webRoot;
    }

    /**
     * The main logic executed by the thread from the thread pool.
     * It reads the HTTP request, processes it, and sends a response.
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            System.out.println("Request received: " + requestLine);

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }
            String uri = parts[1];

            if ("/".equals(uri)) {
                uri = "/index.html";
            }

            Path filePath = Paths.get(webRoot, uri).normalize();

            if (!filePath.startsWith(Paths.get(webRoot))) {
                sendErrorResponse(out, 403, "Forbidden");
                return;
            }

            File file = filePath.toFile();

            // Check if the file exists and is not a directory.
            if (file.exists() && !file.isDirectory()) {
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                sendHeader(out, 200, "OK", file.length(), contentType);

                Files.copy(filePath, out);
                out.flush();
            } else {
                sendErrorResponse(out, 404, "Not Found");
            }

        } catch (IOException e) {
            System.err.println("Error handling the request: " + e.getMessage());
        } finally {
            // Ensure the client socket is closed, regardless of success or failure.
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    /**
     * Sends HTTP response headers to the client.
     * 
     * @param out           The OutputStream connected to the client socket.
     * @param statusCode    The HTTP status code.
     * @param statusMessage The HTTP status message.
     * @param contentLength The length of the content being sent in bytes.
     * @param contentType   The MIME type of the content.
     * @throws IOException If an I/O error occurs while writing to the output
     *                     stream.
     */
    private void sendHeader(OutputStream out, int statusCode, String statusMessage, long contentLength,
            String contentType) throws IOException {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        response.append("Date: ").append(new Date()).append("\r\n");
        response.append("Server: SimpleJavaWebServer/1.0\r\n");
        response.append("Content-Type: ").append(contentType).append("\r\n");
        response.append("Content-Length: ").append(contentLength).append("\r\n");
        response.append("\r\n");
        out.write(response.toString().getBytes("UTF-8"));
    }

    /**
     * Sends an HTTP error response to the client.
     * 
     * @param out           The OutputStream connected to the client socket.
     * @param statusCode    The HTTP status code for the error.
     * @param statusMessage The HTTP status message for the error.
     * @throws IOException If an I/O error occurs while writing to the output
     *                     stream.
     */
    private void sendErrorResponse(OutputStream out, int statusCode, String statusMessage) throws IOException {
        String htmlContent = "<!DOCTYPE html><html><head><title>Error " + statusCode + "</title></head><body><h1>Error "
                + statusCode + ": " + statusMessage
                + "</h1><p>The requested resource could not be found or you do not have permission.</p></body></html>";
        sendHeader(out, statusCode, statusMessage, htmlContent.getBytes("UTF-8").length, "text/html");
        out.write(htmlContent.getBytes("UTF-8"));
        out.flush();
    }
}