package client;

public class UtilsBuild {
    public static final int MAX_BYTES = 516; // Maximum bytes per packet (512 + 4 bytes header)

    // Opcodes for the TFTP requests
    public static final int OP_RRQ = 1;
    public static final int OP_WRQ = 2;
    public static final int OP_DATA = 3;
    public static final int OP_ACK = 4;
    public static final int OP_ERROR = 5;

    // Pack RRQ or WRQ request for "octet" mode
    public static byte[] packRequest(int opcode, String filename) {
        byte[] buf = new byte[MAX_BYTES];
        int length = packOpcode(buf, opcode);
        length += packString(buf, length, filename);
        buf[length++] = 0;  // Null terminator for filename
        length += packString(buf, length, "octet");
        buf[length++] = 0;  // Null terminator for mode
        byte[] packet = new byte[length];
        System.arraycopy(buf, 0, packet, 0, length);
        return packet;
    }

    // Pack DATA packet
    public static byte[] packData(int block, byte[] data, int offset, int length) {
        byte[] buf = new byte[MAX_BYTES];
        int pos = packOpcode(buf, OP_DATA);
        pos += packBlockNumber(buf, pos, block);
        System.arraycopy(data, offset, buf, pos, length);
        pos += length;
        byte[] packet = new byte[pos];
        System.arraycopy(buf, 0, packet, 0, pos);
        return packet;
    }

    // Pack ACK packet
    public static byte[] packAck(int block) {
        byte[] buf = new byte[4];
        int pos = packOpcode(buf, OP_ACK);
        pos += packBlockNumber(buf, pos, block);
        return buf;
    }

    // Utility methods for packing data
    private static int packOpcode(byte[] buf, int opcode) {
        return packUInt16(buf, 0, opcode);
    }

    private static int packBlockNumber(byte[] buf, int offset, int blockNumber) {
        return packUInt16(buf, offset, blockNumber);
    }

    private static int packUInt16(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value >> 8);
        buf[offset + 1] = (byte) value;
        return 2;
    }

    private static int packString(byte[] buf, int offset, String str) {
        byte[] bytes = str.getBytes();
        System.arraycopy(bytes, 0, buf, offset, bytes.length);
        return bytes.length;
    }

    public static int getBlockNumber(byte[] packet) {
        if (packet.length < 4) {
            throw new IllegalArgumentException("Packet too short to contain a block number");
        }
        return ((packet[2] & 0xff) << 8) | (packet[3] & 0xff);
    }

    public static boolean isErrorPacket(byte[] packet) {
        return packet.length >= 2 && ((packet[0] & 0xff) << 8 | (packet[1] & 0xff)) == OP_ERROR;
    }

    public static String extractErrorMessage(byte[] packet) {
        if (isErrorPacket(packet)) {
            int start = 4; // Opcode (2 bytes) + Error Code (2 bytes)
            int end = start;
            while (end < packet.length && packet[end] != 0) {
                end++;
            }
            return new String(packet, start, end - start);
        }
        return "No error message";
    }
}