package ex1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ClientCharCounterNonBlocking {
    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private int s;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer rBi = ByteBuffer.allocate(Byte.BYTES);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<String> arr = new ArrayDeque<>();
        private boolean closed = false;

        private Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
        }

        private void processIn() {

            Reader.ProcessStatus status;
            /*
            do {
                status = packFinalReader.process(bufferIn);
                switch (status) {
                    case DONE:
                        var msg=  packFinalReader.get();

                        packFinalReader.reset();
                        System.out.println(msg.s());
                        break;
                    case REFILL:
                        updateInterestOps();
                        break;
                    case ERROR:
                        closed = true;
                        logger.log(Level.WARNING, "Error durung reading !!!");
                        break;
                }
            } while (status == Reader.ProcessStatus.REFILL);
             */
            updateInterestOps();
        }

        private void processOut() {
            var s = arr.poll();
            if(s != null) {
                var e = StandardCharsets.UTF_8.encode(s);
                bufferOut.putShort((short) e.remaining());
                bufferOut.put(e);
            }
            updateInterestOps();
        }

        private void queueMessage(String  s) {
            arr.add(s);
            processOut();
        }



        private void updateInterestOps() {
            int ops = 0;
            if (!closed && bufferIn.hasRemaining()) {
                ops |= SelectionKey.OP_READ;
            }
            if (bufferOut.position() > 0) {
                ops |= SelectionKey.OP_WRITE;
            }

            if (ops == 0) {
                silentlyClose();
            }
            key.interestOps(ops);
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Performs the read action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doRead and after the call
         *
         * @throws IOException
         */
        private void doRead() throws IOException {
            if (sc.read(bufferIn) == -1) {
                closed = true;
            } else {

                processIn();
                updateInterestOps();

            }


        }

        /**
         * Performs the write action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doWrite and after the call
         *
         * @throws IOException
         */

        private void doWrite() throws IOException {
            bufferOut.flip();
            sc.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();

        }

        public void doConnect() throws IOException {
           if(!sc.finishConnect())
               return;
           key.interestOps(SelectionKey.OP_READ);
        }
    }

        static private int BUFFER_SIZE = 10_000;
        static private Logger logger = Logger.getLogger(ClientCharCounterNonBlocking.class.getName());

        private final SocketChannel sc;
        private final Selector selector;
        private final InetSocketAddress serverAddress;
        private final ArrayBlockingQueue<String> bq = new ArrayBlockingQueue<>(10);
        private final Thread console;
        private Context uniqueContext;

        public ClientCharCounterNonBlocking(InetSocketAddress serverAddress) throws IOException {
            this.serverAddress = serverAddress;
            this.sc = SocketChannel.open();
            this.selector = Selector.open();
            this.console = new Thread(this::consoleRun);
        }

        private void consoleRun() {
            try {
                try (var scanner = new Scanner(System.in)) {
                    while (scanner.hasNextLine()) {
                        var msg = scanner.nextLine();
                        sendCommand(msg);
                    }
                }
                logger.info("Console thread stopping");
            } catch (InterruptedException e) {
                logger.info("Console thread has been interrupted");
            }
        }

        /**
         * Send instructions to the selector via a BlockingQueue and wake it up
         *
         * @param msg
         * @throws InterruptedException
         */

        private void sendCommand(String msg) throws InterruptedException {
            synchronized (bq) {
                bq.put(msg);
                selector.wakeup();
            }
        }

        /**
         * Processes the command from the BlockingQueue
         */
        private void processCommands() {
            synchronized (bq) {
                var line = bq.poll();
                if(line != null) {
                    uniqueContext.queueMessage(line);
                }
            }
        }

        public void launch() throws IOException {
            sc.configureBlocking(false);
            var key = sc.register(selector, SelectionKey.OP_CONNECT);
            uniqueContext = new Context(key);
            key.attach(uniqueContext);
            sc.connect(serverAddress);

            console.start();

            while (!Thread.interrupted()) {
                try {
                    selector.select(this::treatKey);
                    processCommands();
                } catch (UncheckedIOException tunneled) {
                    throw tunneled.getCause();
                }
            }
        }

        private void treatKey(SelectionKey key) {
            try {
                if (key.isValid() && key.isConnectable()) {
                    uniqueContext.doConnect();
                }
                if (key.isValid() && key.isWritable()) {
                    uniqueContext.doWrite();
                }
                if (key.isValid() && key.isReadable()) {
                    uniqueContext.doRead();
                }
            } catch (IOException ioe) {
                // lambda call in select requires to tunnel IOException
                throw new UncheckedIOException(ioe);
            }
        }

        private void silentlyClose(SelectionKey key) {
            Channel sc = (Channel) key.channel();
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        public static void main(String[] args) throws NumberFormatException, IOException {
            if (args.length != 2) {
                usage();
                return;
            }
            new ClientCharCounterNonBlocking( new InetSocketAddress(args[0], Integer.parseInt(args[1]))).launch();
        }

        private static void usage() {
            System.out.println("Usage : ClientCharCounter hostname port");
        }
    }