package ex2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerFixedPrestartedDupCleaner {
    private static final Logger logger = Logger.getLogger(ServerFixedPrestartedDupCleaner.class.getName());
    private final int nbThreads;
    private final ServerSocketChannel serverSocketChannel;

    public ServerFixedPrestartedDupCleaner(int port, int nbThreads) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));

        if (nbThreads < 1)
            throw new IllegalArgumentException();
        this.nbThreads = nbThreads;
    }

    public void launch() {
        for (var i = 0; i < nbThreads; i++) {
            new Thread(() -> {
                SocketChannel client;
                while (!Thread.interrupted()) {
                    try {
                        client = serverSocketChannel.accept();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "IT'S REALLY SERIOUS");
                        return;
                    }

                    try {
                        serve(client);
                    } catch (IOException e) {
                        logger.info("Client doesn't respect protocols");
                    } finally {
                        silentlyClose(client);
                    }

                }
            }).start();
        }
    }

    private void silentlyClose(SocketChannel client) {
        try {
            if (client != null)
                client.close();
        } catch (IOException e) {
            // nothing
        }
    }

    private void serve(SocketChannel client) throws IOException {
        byte oldByte = 0;
        var buffer = ByteBuffer.allocate(Byte.BYTES);
        var bufferSend = ByteBuffer.allocateDirect(Byte.BYTES);
        for (;;) {
            buffer.clear();
            bufferSend.clear();
            if (!readFully(client, buffer))
                return;
            var newByte = buffer.flip().get();

            if (oldByte != newByte) {
                bufferSend.put(newByte);
                oldByte = newByte;
            }

            client.write(bufferSend.flip());
        }
    }

    private static boolean readFully(SocketChannel sc, ByteBuffer bb) throws IOException {
        for (; ; ) {
            if (sc.read(bb) == -1)
                return false;

            if (!bb.hasRemaining())
                return true;
        }
    }


    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            System.out.println("usage: java fr.upem.net.tcp.exam1819.ServerFixedPrestartedDupCleaner port maxThreads");
            return;
        }
        ServerFixedPrestartedDupCleaner server =
                new ServerFixedPrestartedDupCleaner(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
    }
}