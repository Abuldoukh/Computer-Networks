package com.aa2796.tftp.server;

import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class RequestHandler {
    private static final int MAX_DATA_LENGTH = 512;
    private static final int DATA_PACKET_HEADER_LENGTH = 4;
    private DatagramSocket socket;
    private DatagramPacket requestPacket;

    public RequestHandler(DatagramSocket socket, DatagramPacket requestPacket) {
        this.socket = socket;
        this.requestPacket = requestPacket;
    }

    public void handleRequest() throws IOException {
        byte[] data = requestPacket.getData();
        int opcode = ((data[0] & 0xff) << 8) | (data[1] & 0xff);

        switch (opcode) {
            case 1: // RRQ
                handleReadRequest();
                break;
            case 2: // WRQ
                handleWriteRequest();
                break;
            case 3: // DATA
                handleData();
                break;
            case 4: // ACK
                handleAck();
                break;
            case 5: // ERROR
                handleError();
                break;
            default:
                sendError(4, "Illegal TFTP operation.");
        }
    }

    private void handleReadRequest() throws IOException {
        String filename = extractFilename();
        Path filePath = Paths.get(filename);
        if (!Files.exists(filePath)) {
            sendError(1, "File not found");
            return;
        }
        sendFile(filePath);
    }

    private void handleWriteRequest() throws IOException {
        String filename = extractFilename();
        Path filePath = Paths.get(filename);
        if (Files.exists(filePath)) {
            sendError(6, "File already exists");
            return;
        }
        Files.createFile(filePath);
        sendAck(0);
    }

    private void handleData() throws IOException {
        byte[] data = requestPacket.getData();
        int blockNumber = ((data[2] & 0xff) << 8) | (data[3] & 0xff);
        Path filePath = Paths.get(extractFilename());
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile(), true)) {
            fos.write(data, DATA_PACKET_HEADER_LENGTH, requestPacket.getLength() - DATA_PACKET_HEADER_LENGTH);
        }
        sendAck(blockNumber);
    }

    private void handleAck() {

    }

    private void handleError() {
        System.out.println("Received ERROR packet from client.");
    }

    private String extractFilename() {
        byte[] data = requestPacket.getData();
        int len = requestPacket.getLength();
        int i = 2;
        StringBuilder filename = new StringBuilder();
        while (i < len && data[i] != 0) {
            filename.append((char) data[i]);
            i++;
        }
        return filename.toString();
    }

    private void sendFile(Path filePath) throws IOException {
        byte[] fileData = Files.readAllBytes(filePath);
        int blockNumber = 1;
        for (int i = 0; i < fileData.length; i += MAX_DATA_LENGTH) {
            int end = Math.min(fileData.length, i + MAX_DATA_LENGTH);
            byte[] dataPacket = new byte[4 + (end - i)];
            dataPacket[0] = 0;
            dataPacket[1] = 3; // DATA opcode
            dataPacket[2] = (byte) (blockNumber >> 8);
            dataPacket[3] = (byte) (blockNumber & 0xff);
            System.arraycopy(fileData, i, dataPacket, 4, end - i);

            DatagramPacket packet = new DatagramPacket(dataPacket, dataPacket.length, requestPacket.getAddress(), requestPacket.getPort());
            socket.send(packet);
            if (!receiveAck(blockNumber)) {
                break;
            }
            blockNumber++;
        }
    }

    private boolean receiveAck(int blockNumber) {
        byte[] ackBuffer = new byte[4];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        try {
            socket.receive(ackPacket);
            int receivedBlockNumber = ((ackBuffer[2] & 0xff) << 8) | (ackBuffer[3] & 0xff);
            return receivedBlockNumber == blockNumber;
        } catch (IOException e) {
            System.out.println("Timeout or error receiving ACK for block " + blockNumber);
            return false;
        }
    }

    private void sendAck(int blockNumber) throws IOException {
        byte[] ackPacket = new byte[4];
        ackPacket[0] = 0;
        ackPacket[1] = 4; // ACK opcode
        ackPacket[2] = (byte) (blockNumber >> 8);
        ackPacket[3] = (byte) (blockNumber & 0xff);
        DatagramPacket packet = new DatagramPacket(ackPacket, ackPacket.length, requestPacket.getAddress(), requestPacket.getPort());
        socket.send(packet);
    }

    private void sendError(int errorCode, String errorMessage) throws IOException {
        byte[] messageBytes = errorMessage.getBytes();
        byte[] errorPacket = new byte[5 + messageBytes.length];
        errorPacket[0] = 0;
        errorPacket[1] = 5; // ERROR opcode
        errorPacket[2] = (byte) (errorCode >> 8);
        errorPacket[3] = (byte) (errorCode & 0xff);
        System.arraycopy(messageBytes, 0, errorPacket, 4, messageBytes.length);
        errorPacket[errorPacket.length - 1] = 0;
        DatagramPacket packet = new DatagramPacket(errorPacket, errorPacket.length, requestPacket.getAddress(), requestPacket.getPort());
        socket.send(packet);
    }
}
