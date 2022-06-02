package ex1;

import Utils.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ClientCharCounter {

    private final static Charset UTF8 = StandardCharsets.UTF_8;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("usage: java fr.upem.net.tcp.exam1819.ClientCharCounter host port");
            return;
        }

        var bufferIn = ByteBuffer.allocateDirect(1024);
        var bufferOut = ByteBuffer.allocateDirect(Short.BYTES + Short.MAX_VALUE);

        try (var scanner = new Scanner(System.in); var sc = SocketChannel.open()) {
            var serverAddress = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
            sc.connect(serverAddress);
            while (scanner.hasNext()) {
                // writing
                if (!writer(bufferOut, scanner, sc)) continue;
                // reading
                if (reader(bufferIn, sc)) return;
            }
        }
    }

    private static boolean writer(ByteBuffer bufferOut, Scanner scanner, SocketChannel sc) throws IOException {
        bufferOut.clear();
        var line = scanner.next();
        var UTF8 = StandardCharsets.UTF_8;
        var lineLength = line.getBytes(UTF8).length;
        if (lineLength > bufferOut.remaining()) {
            System.out.println("Ignored");
            return false;
        }
        bufferOut.putShort((short) lineLength).put(UTF8.encode(line));
        sc.write(bufferOut.flip());
        return true;
    }

    private static boolean reader(ByteBuffer bufferIn, SocketChannel sc) throws IOException {
        bufferIn.clear();
        if (!Utils.readFully(sc, bufferIn, Short.BYTES)) {
            return true;
        }
        var nbChar = bufferIn.getShort();

        for (int i = 0; i < nbChar; i++) {
            if (!Utils.readFully(sc, bufferIn, Short.BYTES)) {
                return true;
            }
            var occurrence = bufferIn.getShort();

            if (!Utils.readFully(sc, bufferIn, 1)) {
                return true;
            }
            var length = bufferIn.get();
            if (!Utils.readFully(sc, bufferIn, length)) {
                return true;
            }

            System.out.println(UTF8.decode(bufferIn) + " : " + occurrence);
        }
        return false;
    }
}