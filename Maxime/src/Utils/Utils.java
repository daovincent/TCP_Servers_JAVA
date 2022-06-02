package Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Utils {
    public static boolean readFully(SocketChannel sc, ByteBuffer buffer, int length) throws IOException {
        buffer.clear();
        buffer.limit(length);
        while (buffer.hasRemaining()) {
            int read = sc.read(buffer);
            if (read == -1) {
                System.out.println("owshit - Wrong server format");
                return false;
            }
        }
        buffer.flip();
        return true;
    }
}
