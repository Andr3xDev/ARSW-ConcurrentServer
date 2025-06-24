package edu.arsw.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {

    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 10;
    private static final String WEB_ROOT = "resources";

    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor web iniciado en el puerto " + PORT);
            System.out.println("Sirviendo archivos desde: " + WEB_ROOT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nueva conexión entrante de: " + clientSocket.getInetAddress());

                executorService.submit(new RequestHandler(clientSocket, WEB_ROOT));
            }
        } catch (IOException e) {
            System.err.println("Error al iniciar o ejecutar el servidor: " + e.getMessage());
            executorService.shutdown();
        } finally {
            executorService.shutdown();
            System.out.println("Servidor web detenido.");
        }
    }
}