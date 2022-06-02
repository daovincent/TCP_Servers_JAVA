package ex1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ClientCharCounter {
    private static record Context(short nbOccurrences, char car) {}
    private static final Charset charset = StandardCharsets.UTF_8;

    private static Context getContext(SocketChannel sc) throws IOException {
        var buffer = ByteBuffer.allocate(Short.BYTES + Byte.BYTES);
        if (readFully(sc, buffer)) {
            buffer.flip();
            var nbOccurrences = buffer.getShort();
            var sizeOfChar = buffer.get();
            var secondBuffer = ByteBuffer.allocate(sizeOfChar);
            if (readFully(sc, secondBuffer)) {
                secondBuffer.flip();
                return new Context(nbOccurrences, charset.decode(secondBuffer).charAt(0));
            }
        }
        throw new IllegalStateException("Server don't respect the protocol");
    }

    private static List<Context> getResult(SocketChannel sc, String line) throws IOException {
        var bufferLine = charset.encode(line);
        var size = (short) bufferLine.limit();
        if (size > Short.MAX_VALUE)
            return new ArrayList<>();

        var bufferSend = ByteBuffer.allocateDirect(Short.BYTES + size);
        bufferSend.putShort(size).put(bufferLine).flip();
        sc.write(bufferSend);
        var bufferReceive = ByteBuffer.allocateDirect(Short.BYTES);

        var list = new ArrayList<Context>();
        if (readFully(sc, bufferReceive)) {
            var sizeDiffChar = bufferReceive.flip().getShort();
            for (var i = 0; i < sizeDiffChar; i++) {
               list.add(getContext(sc));
            }
        }
        return list;
    }

    private static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
        for (; ; ) {
            if (sc.read(bb) == -1)
                return false;

            if (!bb.hasRemaining())
                return true;
        }
    }


    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }

        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        try (var sc = SocketChannel.open(server)) {
            var scanner = new Scanner(System.in);
            while (true) {
                try {
                    var str = scanner.nextLine();
                    for (var x : getResult(sc, str)) {
                        System.out.println(x.car + " : " + x.nbOccurrences);
                    }
                } catch (NoSuchElementException e) {
                    break;
                }

            }
        }
        System.out.println("END");
    }

    private static void usage() {
        System.out.println("Client Char Counter address port");
    }
}
