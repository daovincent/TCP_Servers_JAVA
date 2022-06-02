package ex3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

public class ServerNonBlockingDupCleaner {
    final static private Logger logger = Logger.getLogger(ServerNonBlockingDupCleaner.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;

    private final ConsoleThreadSafe consoleThreadSafe = new ConsoleThreadSafe();

    private final HashMap<SelectionKey, Integer> map = new HashMap<>();
    private int deletedCounter;
    private State state = State.WORKING;

    public ServerNonBlockingDupCleaner(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        selector = Selector.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
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

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        var consoleRun = new Thread(this::consoleRun);
        consoleRun.setDaemon(true);
        consoleRun.start();
        while (!Thread.interrupted() && state != State.SHUTDOWN_NOW) {
            printKeys(); // for debug
            try {
                selector.select(this::treatKey);
                processCommand();
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
    }

    private void processCommand() {
        var cmd = consoleThreadSafe.proccessCommand();
        if (cmd == null) {
            return;
        }
        switch (cmd) {
            case "INFO" -> printInfo();
            case "SHUTDOWN" -> state = State.SHUTDOWN;
            case "SHUTDOWNNOW" -> state = State.SHUTDOWN_NOW;
            default -> {
            }
        }
    }

    private void treatKey(SelectionKey key) throws UncheckedIOException {
        try {
            if (key.isValid() && key.isAcceptable()) {
                if (state == State.SHUTDOWN) {
                    key.channel().close();
                    return;
                }
                doAccept(key);
            }
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        // only the ServerSocketChannel is registered in OP_ACCEPT
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssc.accept();
        if (sc == null) {
            return; // the selector gave a bad hint
        }
        sc.configureBlocking(false);
        var sk = sc.register(selector, SelectionKey.OP_READ);
        sk.attach(new Context(sk, this));
        incrementDeleted(sk, 0);
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

    private void consoleRun() {
        while (!Thread.interrupted()) {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNext()) {
                    consoleThreadSafe.sendCommand(scanner.next());
                    selector.wakeup();
                }
            }
        }
    }

    private enum State {
        WORKING, SHUTDOWN, SHUTDOWN_NOW
    }

    private static class ConsoleThreadSafe {
        private final Object lock = new Object();
        private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);

        public void sendCommand(String command) {
            synchronized (lock) {
                queue.add(command);
            }
        }

        public String proccessCommand() {
            synchronized (lock) {
                return queue.poll();
            }
        }
    }


    public void deleteClient(SelectionKey client) {
            map.remove(client);
    }

    public void incrementDeleted(SelectionKey client, int increment) {
            deletedCounter += increment;
            map.merge(client, increment, Integer::sum);
    }

    public void printInfo() {
        System.out.println("Number of bytes deleted in total: " + deletedCounter);
        map.forEach((key, integer) -> System.out.println("Client with address " + key.channel() + " deleted " + integer + " bytes since its connection"));
    }

    private static class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocateDirect(1024);
        private final ByteBuffer bufferOut = ByteBuffer.allocateDirect(1024);
        private final ServerNonBlockingDupCleaner server;
        private boolean closed;

        Context(SelectionKey key, ServerNonBlockingDupCleaner server) {
            this.key = key;
            this.server = server;
            this.sc = (SocketChannel) key.channel();
        }

        public void doWrite() throws IOException {
            sc.write(bufferOut.flip());
            bufferOut.compact();
            updateInterestOps(key);
        }

        public void updateInterestOps(SelectionKey key) {
            var ops = 0;

            if (!closed && bufferIn.hasRemaining()) {
                ops |= SelectionKey.OP_READ;
            }

            if (bufferOut.position() != 0) {
                ops |= SelectionKey.OP_WRITE;
            }

            if (ops == 0) {
                silentlyClose();
                return;
            }
            key.interestOps(ops);
        }

        private void silentlyClose() {
            try {
                closed = false;
                server.deleteClient(key);
                sc.close();
            } catch (IOException ignored) {
                // ignored
            }
        }

        public void doRead() throws IOException {
            closed = (sc.read(bufferIn)) == -1;
            process();
            updateInterestOps(key);
        }

        private void process() {
            bufferIn.flip();
            if (closed && !bufferIn.hasRemaining()) {
                return;
            }
            bufferOut.clear();
            var deleted = 0;
            if (!(bufferIn.hasRemaining() || bufferOut.hasRemaining())) {
                return;
            }

            byte lastByte = bufferIn.get();
            bufferOut.put(lastByte);
            while (bufferIn.hasRemaining() && bufferOut.hasRemaining()) {
                var actualByte = bufferIn.get();
                if (actualByte != lastByte) {
                    lastByte = actualByte;
                    bufferOut.put(actualByte);
                }else {
                    deleted++;
                }
            }

            server.incrementDeleted(key, deleted);
            bufferIn.compact();
        }
    }
}