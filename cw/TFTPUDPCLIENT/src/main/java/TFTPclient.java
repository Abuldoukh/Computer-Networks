import java.io.*;
import java.net.*;

public class TFTPclient {
    private static final int SERVER_PORT = 69;
    private static final int MAX_DATA_LENGTH = 512;

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java TFTPClient [server_ip] [mode] [local_filename] [remote_filename]");
            return;
        }

        String serverIp = args[0];
        String mode = args[1];
        String localFilename = args[2];
        String remoteFilename = args[3];

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress serverAddress = InetAddress.getByName(serverIp);

            // Send request packet
            byte[] request = createRequestPacket(mode, remoteFilename);
            DatagramPacket requestPacket = new DatagramPacket(request, request.length, serverAddress, SERVER_PORT);
            socket.send(requestPacket);

            if (mode.equalsIgnoreCase("read")) {
                receiveFile(socket, localFilename);
            } else if (mode.equalsIgnoreCase("write")) {
                sendFile(socket, serverAddress, localFilename);
            } else {
                System.out.println("Invalid mode. Use 'read' or 'write'.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static byte[] createRequestPacket(String mode, String filename) {
        byte opcode = (byte) (mode.equalsIgnoreCase("read") ? 1 : 2);
        byte[] filenameBytes = filename.getBytes();
        byte[] modeBytes = "octet".getBytes(); // TFTP mode is usually octet
        byte[] request = new byte[filenameBytes.length + modeBytes.length + 5];

        request[0] = 0; // Opcode
        request[1] = opcode;

        System.arraycopy(filenameBytes, 0, request, 2, filenameBytes.length);
        request[filenameBytes.length + 2] = 0; // Zero byte after filename

        System.arraycopy(modeBytes, 0, request, filenameBytes.length + 3, modeBytes.length);
        request[request.length - 1] = 0; // Zero byte after mode

        return request;
    }

    private static void receiveFile(DatagramSocket socket, String localFilename) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(localFilename);
        int blockNumber = 1;
        while (true) {
            byte[] receiveData = new byte[MAX_DATA_LENGTH + 4];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);
            byte[] data = receivePacket.getData();
            int opcode = ((data[0] & 0xff) << 8) | (data[1] & 0xff);
            if (opcode == 5) {
                // Error Packet Received
                System.out.println("Error Packet Received: " + new String(data, 4, receivePacket.getLength() - 4));
                return;
            } else if (opcode != 3) {
                // Unexpected packet received
                System.out.println("Unexpected Packet Received with opcode: " + opcode);
                return;
            }
            int receivedBlockNumber = ((data[2] & 0xff) << 8) | (data[3] & 0xff);
            if (receivedBlockNumber == blockNumber) {
                fileOutputStream.write(data, 4, receivePacket.getLength() - 4);
                sendAck(socket, receivePacket.getPort(), receivedBlockNumber);
                blockNumber++;
                if (receivePacket.getLength() < MAX_DATA_LENGTH + 4) {
                    // Last Packet Received
                    break;
                }
            } else if (receivedBlockNumber < blockNumber) {
                // Duplicate Packet Received, Resend ACK
                sendAck(socket, receivePacket.getPort(), receivedBlockNumber);
            }
        }
        fileOutputStream.close();
        System.out.println("File Received Successfully: " + localFilename);
    }

    private static void sendFile(DatagramSocket socket, InetAddress serverAddress, String localFilename) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(localFilename);
        byte[] sendData = new byte[MAX_DATA_LENGTH + 4];
        int blockNumber = 1;
        while (true) {
            int bytesRead = fileInputStream.read(sendData, 4, MAX_DATA_LENGTH);
            if (bytesRead == -1) {
                // End of file
                break;
            }
            sendData[0] = 0;
            sendData[1] = 3; // DATA Opcode
            sendData[2] = (byte) (blockNumber >> 8);
            sendData[3] = (byte) (blockNumber);
            DatagramPacket sendPacket = new DatagramPacket(sendData, bytesRead + 4, serverAddress, SERVER_PORT);
            socket.send(sendPacket);
            blockNumber++;
            waitForAck(socket, blockNumber);
        }
        fileInputStream.close();
        System.out.println("File Sent Successfully: " + localFilename);
    }

    private static void waitForAck(DatagramSocket socket, int blockNumber) throws IOException {
        byte[] receiveData = new byte[4];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        while (true) {
            try {
                socket.setSoTimeout(1000); // 1 second timeout for ACK
                socket.receive(receivePacket);
                byte[] data = receivePacket.getData();
                int opcode = ((data[0] & 0xff) << 8) | (data[1] & 0xff);
                if (opcode == 5) {
                    // Error Packet Received
                    System.out.println("Error Packet Received: " + new String(data, 4, receivePacket.getLength() - 4));
                    return;
                } else if (opcode != 4) {
                    // Unexpected packet received
                    System.out.println("Unexpected Packet Received with opcode: " + opcode);
                    return;
                }
                int receivedBlockNumber = ((data[2] & 0xff) << 8) | (data[3] & 0xff);
                if (receivedBlockNumber == blockNumber) {
                    // ACK received for the current block
                    return;
                }
            } catch (SocketTimeoutException e) {
                // Resend the data packet on timeout
                System.out.println("Timeout waiting for ACK, resending packet: " + blockNumber);
                return;
            }
        }
    }

    private static void sendAck(DatagramSocket socket, int port, int blockNumber) throws IOException {
        byte[] ackData = new byte[4];
        ackData[0] = 0;
        ackData[1] = 4; // ACK Opcode
        ackData[2] = (byte) (blockNumber >> 8);
        ackData[3] = (byte) (blockNumber);
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, InetAddress.getLocalHost(), port);
        socket.send(ackPacket);
    }
}
