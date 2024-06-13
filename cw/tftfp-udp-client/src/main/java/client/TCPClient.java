package client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class TCPClient {
    private String serverAddress;
    private int serverPort;

    public TCPClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public void startClient() {
        Scanner scanner = new Scanner(System.in);
        try (Socket socket = new Socket(serverAddress, serverPort);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            System.out.println("Connected to server at " + serverAddress + ":" + serverPort);
            System.out.println("Enter command ('read' or 'write'), followed by filename:");

            String command = scanner.nextLine();
            String[] tokens = command.split(" ");
            if (tokens.length != 2) {
                System.out.println("Invalid command. Usage: <read/write> <filename>");
                return;
            }

            String action = tokens[0];
            String filename = tokens[1];

            if ("read".equalsIgnoreCase(action)) {
                dos.writeUTF("RRQ");
                dos.writeUTF(filename);
                receiveFile(dis, filename);
            } else if ("write".equalsIgnoreCase(action)) {
                dos.writeUTF("WRQ");
                dos.writeUTF(filename);
                sendFile(dos, filename);
            } else {
                System.out.println("Unknown command. Use 'read' or 'write'.");
            }

        } catch (UnknownHostException e) {
            System.out.println("Server not found: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("I/O Error: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    private void receiveFile(DataInputStream dis, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            int bytesRead;
            while ((bytesRead = dis.readInt()) != -1) {
                byte[] buffer = new byte[bytesRead];
                dis.readFully(buffer, 0, bytesRead);
                fos.write(buffer);
            }
            System.out.println("File downloaded successfully: " + filename);
        }
    }

    private void sendFile(DataOutputStream dos, String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("File not found: " + filename);
            return;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.writeInt(bytesRead);
                dos.write(buffer, 0, bytesRead);
            }
            dos.writeInt(-1);
            System.out.println("File uploaded successfully: " + filename);
        }
    }

    public static void main(String[] args) {
        TCPClient client = new TCPClient("localhost", 8888);
        client.startClient();
    }
}
