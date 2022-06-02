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
    private final ServerSocketChannel ssc = ServerSocketChannel.open();
    private final int nbThreads;
    // TODO

    public ServerFixedPrestartedDupCleaner(int port, int nbThreads) throws IOException {
        ssc.bind(new InetSocketAddress(port));
        this.nbThreads = nbThreads;
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 2) {
            System.out.println("usage: java fr.upem.net.tcp.exam1819.ServerFixedPrestartedDupCleaner port maxThreads");
            return;
        }
        ServerFixedPrestartedDupCleaner server = new ServerFixedPrestartedDupCleaner(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        server.launch();
    }

    private void handleClient() {
        while (!Thread.interrupted()) {
            SocketChannel client = null;
            try {
                client = ssc.accept();
                logger.info("Connection accepted from " + client.getRemoteAddress());
                serve(client);
            } catch (IOException ioe) {
                logger.log(Level.INFO, "Connection terminated by IOException ", ioe.getCause());
            } finally {
                silentlyClose(client);
            }
        }
    }

    private void silentlyClose(SocketChannel client) {
        try {
            client.close();
        } catch (IOException ignored) {
            // ignored
        }
    }

    public void launch() {
        for (int i = 0; i < nbThreads; i++) {
            new Thread(this::handleClient).start();
        }
    }

    private void serve(SocketChannel client) throws IOException {
        var bufferIn = ByteBuffer.allocate(1024);
        var bufferOut = ByteBuffer.allocate(1024);
        while (!Thread.interrupted()) {
            bufferIn.clear();
            var read = client.read(bufferIn);
            bufferIn.flip();
            if (read == -1) {
                System.out.println("Client " + client.getRemoteAddress() + " closed connection");
            }
            bufferOut.clear();
            byte lastByte = bufferIn.get();
            bufferOut.put(lastByte);
            while (bufferIn.hasRemaining()) {
                var actualByte = bufferIn.get();
                if (actualByte != lastByte) {
                    lastByte = actualByte;
                    bufferOut.put(actualByte);
                }
            }

            client.write(bufferOut.flip());
        }
    }
}