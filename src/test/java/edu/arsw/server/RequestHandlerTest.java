package edu.arsw.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the RequestHandler class, simulating HTTP requests and server
 * responses.
 * It uses a temporary directory for web resources and mocks the client socket.
 */
class RequestHandlerTest {

    @TempDir
    Path tempDir;
    private Path webRootPath;
    private ByteArrayOutputStream simulatedOut;

    @BeforeEach
    void setUp() throws IOException {
        webRootPath = tempDir.resolve("test_resources");
        Files.createDirectory(webRootPath);

        Files.writeString(webRootPath.resolve("index.html"), "<html><body>Hello Test!</body></html>");
        Files.writeString(webRootPath.resolve("styles.css"), "body { color: red; }");
        Files.write(webRootPath.resolve("image.jpg"), new byte[] { 0x01, 0x02, 0x03 });
    }

    @AfterEach
    void tearDown() throws IOException {
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private ByteArrayOutputStream executeRequest(String httpRequest) throws IOException {
        InputStream simulatedIn = new ByteArrayInputStream(httpRequest.getBytes());
        simulatedOut = new ByteArrayOutputStream();

        Socket mockSocket = mock(Socket.class);
        when(mockSocket.getInputStream()).thenReturn(simulatedIn);
        when(mockSocket.getOutputStream()).thenReturn(simulatedOut);

        RequestHandler handler = new RequestHandler(mockSocket, webRootPath.toString());
        handler.run();

        verify(mockSocket).close();
        return simulatedOut;
    }

    @Test
    @DisplayName("Should serve 'index.html' by default for the '/' path")
    void shouldServeDefaultIndexHtml() throws IOException {
        String httpRequest = "GET / HTTP/1.1\r\nHost: localhost:8080\r\n\r\n";
        ByteArrayOutputStream responseStream = executeRequest(httpRequest);
        String response = responseStream.toString();

        assertTrue(response.contains("HTTP/1.1 200 OK"));
        assertTrue(response.contains("Content-Type: text/html"));
        assertTrue(response.contains("<html><body>Hello Test!</body></html>"));
    }

    @Test
    @DisplayName("Should serve a requested CSS file")
    void shouldServeCssFile() throws IOException {
        String httpRequest = "GET /styles.css HTTP/1.1\r\nHost: localhost:8080\r\n\r\n";
        ByteArrayOutputStream responseStream = executeRequest(httpRequest);
        String response = responseStream.toString();

        assertTrue(response.contains("HTTP/1.1 200 OK"));
        assertTrue(response.contains("Content-Type: text/css"));
        assertTrue(response.contains("Content-Length: 20"));
        assertTrue(response.contains("body { color: red; }"));
    }

    @Test
    @DisplayName("Should serve an image file (JPG)")
    void shouldServeImageFile() throws IOException {
        String httpRequest = "GET /image.jpg HTTP/1.1\r\nHost: localhost:8080\r\n\r\n";
        ByteArrayOutputStream responseStream = executeRequest(httpRequest);
        byte[] responseBytes = responseStream.toByteArray();

        String header = new String(responseBytes, 0, Math.min(responseBytes.length, 200));

        assertTrue(header.contains("HTTP/1.1 200 OK"));
        assertTrue(header.contains("Content-Type: image/jpeg"));
        assertTrue(header.contains("Content-Length: 3"));

        int headerEndIndex = header.indexOf("\r\n\r\n") + 4;
        byte[] contentBytes = new byte[responseBytes.length - headerEndIndex];
        System.arraycopy(responseBytes, headerEndIndex, contentBytes, 0, contentBytes.length);

        assertArrayEquals(new byte[] { 0x01, 0x02, 0x03 }, contentBytes);
    }

    @Test
    @DisplayName("Should return 404 Not Found if the file does not exist")
    void shouldReturn404ForNonExistingFile() throws IOException {
        String httpRequest = "GET /nonexistent.html HTTP/1.1\r\nHost: localhost:8080\r\n\r\n";
        ByteArrayOutputStream responseStream = executeRequest(httpRequest);
        String response = responseStream.toString();

        assertTrue(response.contains("HTTP/1.1 404 Not Found"));
        assertTrue(response.contains("Error 404: Not Found"));
    }

    @Test
    @DisplayName("Should return 403 Forbidden for 'path traversal' attempts")
    void shouldReturn403ForPathTraversal() throws IOException {
        String httpRequest = "GET /../passwd HTTP/1.1\r\nHost: localhost:8080\r\n\r\n";
        ByteArrayOutputStream responseStream = executeRequest(httpRequest);
        String response = responseStream.toString();

        assertTrue(response.contains("HTTP/1.1 403 Forbidden"));
        assertTrue(response.contains("Error 403: Forbidden"));
    }

}