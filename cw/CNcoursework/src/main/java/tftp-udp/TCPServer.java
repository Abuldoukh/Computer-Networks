package com.aa2796.tftp.server;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
    private int port;
    private ServerSocket serverSocket;

    public TCPServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started and listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from " + clientSocket.getInetAddress().getHostAddress());
                com.aa2796.tftp.server.ClientHandler clientThread = new com.aa2796.tftp.server.ClientHandler(clientSocket);
                clientThread.start();
            }
        } catch (IOException e) {
            System.out.println("Error when attempting to listen on port " + port + " or listening for a connection");
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        int port = 8888;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        TCPServer server = new TCPServer(port);
        server.start();
    }
}