package client;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TFTPClient {
    private static final int SERVER_PORT = 69;
    private static final int TIMEOUT = 10000; // Timeout in milliseconds
    private static final int MAX_DATA_LENGTH = 512;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java TFTPClient [server_ip] [mode] [filename]");
            return;
        }

        String serverIp = args[0];
        String mode = args[1];
        String filename = args[2];

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT);

            InetAddress serverAddress = InetAddress.getByName(serverIp);

            // Sending read or write request based on the mode
            byte[] request = createRequest(mode, filename);
            DatagramPacket requestPacket = new DatagramPacket(request, request.length, serverAddress, SERVER_PORT);
            socket.send(requestPacket);

            if (mode.equalsIgnoreCase("read")) {
                receiveFile(socket, filename);
            } else if (mode.equalsIgnoreCase("write")) {
                sendFile(socket, serverAddress, filename);
            } else {
                System.out.println("Invalid mode. Use 'read' or 'write'.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] createRequest(String mode, String filename) {
        // Creating read or write request packet
        byte opcode = (byte) (mode.equalsIgnoreCase("read") ? 1 : 2);
        byte[] filenameBytes = filename.getBytes();
        byte[] modeBytes = "octet".getBytes(); // TFTP mode is usually octet
        byte[] request = new byte[filenameBytes.length + modeBytes.length + 4];

        request[0] = 0; // Opcode
        request[1] = opcode;

        System.arraycopy(filenameBytes, 0, request, 2, filenameBytes.length);
        request[filenameBytes.length + 2] = 0; // Zero byte after filename

        System.arraycopy(modeBytes, 0, request, filenameBytes.length + 3, modeBytes.length);
        request[request.length - 1] = 0; // Zero byte after mode

        return request;
    }

    private static void receiveFile(DatagramSocket socket, String filename) throws IOException {
        Path filePath = Paths.get(filename);
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            while (true) {
                byte[] buffer = new byte[MAX_DATA_LENGTH + 4]; // Maximum packet size in TFTP
                DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(dataPacket);

                byte[] data = dataPacket.getData();
                int opcode = ((data[0] & 0xff) << 8) | (data[1] & 0xff);

                if (opcode == 3) { // DATA packet
                    fos.write(data, 4, dataPacket.getLength() - 4); // Write data to file
                    sendAck(socket, dataPacket.getAddress(), dataPacket.getPort(), data[2], data[3]);
                    if (dataPacket.getLength() < MAX_DATA_LENGTH + 4) {
                        break; // Last packet received
                    }
                } else if (opcode == 5) { // ERROR packet
                    System.out.println("Error occurred: " + new String(data, 4, dataPacket.getLength() - 4));
                    break;
                } else {
                    System.out.println("Unexpected packet received.");
                    break;
                }
            }
        }
    }

    private static void sendAck(DatagramSocket socket, InetAddress address, int port, byte blockNumberHigh, byte blockNumberLow) throws IOException {
        byte[] ack = new byte[] {0, 4, blockNumberHigh, blockNumberLow};
        DatagramPacket ackPacket = new DatagramPacket(ack, ack.length, address, port);
        socket.send(ackPacket);
    }

    private static void sendFile(DatagramSocket socket, InetAddress serverAddress, String filename) throws IOException {
        Path filePath = Paths.get(filename);
        byte[] fileData = Files.readAllBytes(filePath);

        int blockNumber = 1;
        int offset = 0;

        while (offset < fileData.length) {
            int end = Math.min(offset + MAX_DATA_LENGTH, fileData.length);
            byte[] dataPacket = new byte[4 + (end - offset)];
            dataPacket[0] = 0;
            dataPacket[1] = 3; // DATA opcode
            dataPacket[2] = (byte) (blockNumber >> 8);
            dataPacket[3] = (byte) (blockNumber & 0xff);
            System.arraycopy(fileData, offset, dataPacket, 4, end - offset);

            DatagramPacket packet = new DatagramPacket(dataPacket, dataPacket.length, serverAddress, SERVER_PORT);
            socket.send(packet);

            // Wait for ACK
            byte[] ackBuffer = new byte[4];
            DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
            socket.receive(ackPacket);

            int receivedBlockNumber = ((ackBuffer[2] & 0xff) << 8) | (ackBuffer[3] & 0xff);
            if (receivedBlockNumber != blockNumber) {
                // Handle error, resend packet or abort
                System.err.println("Received ACK for unexpected block number: " + receivedBlockNumber);
                return;
            }

            blockNumber++;
            offset = end;
        }

        System.out.println("File successfully sent.");
    }
}