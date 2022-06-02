package ex3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerNonBlockingDupCleaner {
    private class Context {
        final private SelectionKey key;
        final private SocketChannel sc;
        private ByteBuffer bufferReader;
        private ByteBuffer bufferSend;
        private boolean closed;
        private static int BUFFER_SIZE = 1024;

        private byte oldByte = 0;
        private int byteDelete;

        Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            bufferReader = ByteBuffer.allocate(BUFFER_SIZE);
            bufferSend = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }

        private void process() {
            bufferSend.clear();
            bufferReader.flip();

            for (var i = 0; i < bufferReader.limit(); i++) {
                var newByte = bufferReader.get();
                if (oldByte != newByte) {
                    bufferSend.put(newByte);
                    oldByte = newByte;
                } else {
                    byteDelete++;
                    nbOctetsDelete++;
                }
            }
            bufferReader.compact();
        }

        private void updateInterestOps() {
            var newInterestOps = 0;
            if (bufferReader.hasRemaining() && !closed)
                newInterestOps = newInterestOps | SelectionKey.OP_READ;
            if (bufferSend.position() != 0)
                newInterestOps = newInterestOps | SelectionKey.OP_WRITE;

            if (newInterestOps == 0) {
                silentlyClose(key);
            } else
                key.interestOps(newInterestOps);
        }

        private void doRead() throws IOException {
            bufferReader.clear();
            if (sc.read(bufferReader) == -1) {
                closed = true;
                return;
            }

            process();
            updateInterestOps();

        }

        private void doWrite() throws IOException {
            bufferSend.flip();
            sc.write(bufferSend);
            bufferSend.compact();
            updateInterestOps();
        }
    }

    final static private Logger logger = Logger.getLogger(ServerNonBlockingDupCleaner.class.getName());

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Thread console;
    private int nbOctetsDelete;

    public ServerNonBlockingDupCleaner(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        if (port < 1024)
            throw new IllegalArgumentException();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        console = new Thread(this::treatConsole);
    }


    public void treatConsole() {
        var scanner = new Scanner(System.in);
        while (!Thread.interrupted()) {
            if (!scanner.hasNextLine())
                break;
            var str = scanner.nextLine();
            switch (str) {
                case "INFO":
                    System.out.println("Number bytes delete " + nbOctetsDelete);
                    for (var key : selector.keys()) {
                        var context = (Context) key.attachment();
                        if (context == null)
                            continue;
                        System.out.println(context.sc + " : " + context.byteDelete);
                    }
                    break;
                case "SHUTDOWN":
                    for (var key : selector.keys()) {
                        silentlyClose(key);
                    }
                    try {
                        serverSocketChannel.close();
                        selector.close();
                    } catch (IOException e) {
                        logger.info("SHUTDOWN");
                        break;
                    }
                    Thread.currentThread().interrupt();

                    break;
                case "SHUTDOWNNOW":
                    try {
                        serverSocketChannel.close();
                        selector.close();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "SERVER END");
                        break;
                    }
                    Thread.currentThread().interrupt();

                    break;
            }
        }

    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        console.start();
        try {
            while (!Thread.interrupted()) {
                printKeys(); // for debug
                selector.select(this::treatKey);
            }
         } catch (ClosedSelectorException e) {
            console.interrupt();
            return;
        }
        catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private void doAccept() throws IOException {
        var client = serverSocketChannel.accept();
        if (client == null) {
            logger.warning("the selector lied to me");
            return;
        }
        client.configureBlocking(false);
        var clientKey = client.register(selector, SelectionKey.OP_READ);
        clientKey.attach(new Context(clientKey));
    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isAcceptable() && key.isValid()) {
                doAccept();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            if (key.isReadable() && key.isValid()) {
                ((Context) key.attachment()).doRead();
            }
            if (key.isWritable() && key.isValid()) {
                ((Context) key.attachment()).doWrite();
            }
        } catch (IOException e) {
            logger.info("Client doesn't respect protocols");
            silentlyClose(key);
        }


    }

    private static void usage() {
        System.out.println("Usage : ServerNonBlockingDupCleaner port");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerNonBlockingDupCleaner(Integer.parseInt(args[0])).launch();
    }

    /***
     ***  Methods below are here to help understanding the behavior of the selector
     ***/

    private String interestOpsToString(SelectionKey key) {
        if (!key.isValid()) {
            return "CANCELLED";
        }
        int interestOps = key.interestOps();
        ArrayList<String> list = new ArrayList<>();
        if ((interestOps & SelectionKey.OP_ACCEPT) != 0) list.add("OP_ACCEPT");
        if ((interestOps & SelectionKey.OP_READ) != 0) list.add("OP_READ");
        if ((interestOps & SelectionKey.OP_WRITE) != 0) list.add("OP_WRITE");
        return String.join("|", list);
    }

    public void printKeys() {
        Set<SelectionKey> selectionKeySet = selector.keys();
        if (selectionKeySet.isEmpty()) {
            System.out.println("The selector contains no key : this should not happen!");
            return;
        }
        System.out.println("The selector contains:");
        for (SelectionKey key : selectionKeySet) {
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
            }
        }
    }

    private String remoteAddressToString(SocketChannel sc) {
        try {
            return sc.getRemoteAddress().toString();
        } catch (IOException e) {
            return "???";
        }
    }

    public void printSelectedKey(SelectionKey key) {
        SelectableChannel channel = key.channel();
        if (channel instanceof ServerSocketChannel) {
            System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
        } else {
            SocketChannel sc = (SocketChannel) channel;
            System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
        }
    }

    private String possibleActionsToString(SelectionKey key) {
        if (!key.isValid()) {
            return "CANCELLED";
        }
        ArrayList<String> list = new ArrayList<>();
        if (key.isAcceptable()) list.add("ACCEPT");
        if (key.isReadable()) list.add("READ");
        if (key.isWritable()) list.add("WRITE");
        return String.join(" and ", list);
    }
}
