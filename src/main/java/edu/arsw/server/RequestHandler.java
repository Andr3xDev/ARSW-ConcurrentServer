package edu.arsw.server;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

public class RequestHandler implements Runnable {

    private Socket clientSocket;
    private String webRoot;

    public RequestHandler(Socket clientSocket, String webRoot) {
        this.clientSocket = clientSocket;
        this.webRoot = webRoot;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            System.out.println("Solicitud recibida: " + requestLine);

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
            System.err.println("Error al manejar la solicitud: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error al cerrar el socket del cliente: " + e.getMessage());
            }
        }
    }

    private void sendHeader(OutputStream out, int statusCode, String statusMessage, long contentLength,
            String contentType) throws IOException {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        response.append("Date: ").append(new Date()).append("\r\n");
        response.append("Server: SimpleJavaWebServer/1.0\r\n");
        response.append("Content-Type: ").append(contentType).append("\r\n");
        response.append("Content-Length: ").append(contentLength).append("\r\n");
        response.append("\r\n"); // Línea en blanco para finalizar las cabeceras
        out.write(response.toString().getBytes("UTF-8"));
    }

    private void sendErrorResponse(OutputStream out, int statusCode, String statusMessage) throws IOException {
        String htmlContent = "<!DOCTYPE html><html><head><title>Error " + statusCode + "</title></head><body><h1>Error "
                + statusCode + ": " + statusMessage
                + "</h1><p>El recurso solicitado no pudo ser encontrado o no tienes permiso.</p></body></html>";
        sendHeader(out, statusCode, statusMessage, htmlContent.getBytes("UTF-8").length, "text/html");
        out.write(htmlContent.getBytes("UTF-8"));
        out.flush();
    }
}