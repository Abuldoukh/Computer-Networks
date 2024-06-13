package com.aa2796.tftp.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler extends Thread {
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

            // Reading the message from client
            String receivedText = dis.readUTF();
            System.out.println("Message from client: " + receivedText);

            // Echoing the message back to client
            dos.writeUTF("Server Echo: " + receivedText);

        } catch (IOException e) {
            System.out.println("Communication error with client " + clientSocket.getInetAddress());
            System.out.println(e.getMessage());
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Failed to close the client socket.");
            }
        }
    }
}