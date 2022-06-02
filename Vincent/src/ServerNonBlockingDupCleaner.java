package ex3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerNonBlockingDupCleaner {
	private class Context {
		final private SelectionKey key;
        final private SocketChannel sc;

		private static int BUFFER_SIZE=1024;
		private ByteBuffer bufferIn=ByteBuffer.allocate(BUFFER_SIZE);
		private ByteBuffer bufferOut=ByteBuffer.allocate(BUFFER_SIZE);
		private boolean closed;
		private int deletedBytes=0;

		Context(SelectionKey key){
            this.key = key;
            this.sc = (SocketChannel) key.channel();
        }
		// do what the server is supposed to do
		private void process(){
			bufferOut.clear();
			bufferIn.flip();
			byte old=0;
			for(int i=0; i<bufferIn.limit();i++){
				var next=bufferIn.get();
				if(old!=next)
					bufferOut.put(next);
				else{
					deletedBytes++;
					totalDeleted++;
				}
				old=next;
			}
			bufferIn.compact();
		}
		private void updateInterestOps(){
			var ops=0;
			if(bufferIn.hasRemaining() && !closed) ops |= SelectionKey.OP_READ;
			if(bufferOut.position()!=0) ops|=SelectionKey.OP_WRITE;
			if(ops==0) silentlyClose(key);
		}


		private void doRead() throws IOException {
			bufferIn.clear();
			if(sc.read(bufferIn)==-1){
				closed=true;
				return;
			}
			process();
			updateInterestOps();
		}
		private void doWrite() throws IOException {
			bufferOut.flip();
			sc.write(bufferOut);
			bufferOut.compact();
			updateInterestOps();
		}
		private void silentlyClose(SelectionKey key) {
			var sc = (Channel) key.channel();
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}
	}
	
	final static private Logger logger = Logger.getLogger(ServerNonBlockingDupCleaner.class.getName());
    
	private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    // TODO
    private int totalDeleted=0;
    public ServerNonBlockingDupCleaner(int port) throws IOException {
    	// Start the server
		this.serverSocketChannel=ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector=Selector.open();
		System.out.println("Started the server on port "+port);
		new Thread(()->{
			var scan=new Scanner(System.in);
			while(!Thread.interrupted()){
				if(!scan.hasNextLine()) break;
				var line=scan.nextLine();
				switch(line){
					case "INFO"->{
						System.out.println("Number bytes deleted :"+totalDeleted);
						for(var key : selector.keys()){
							var context=(Context) key.attachment();
							if(context==null) continue;
							System.out.println(context.sc + " : "+ context.deletedBytes);
						}
					}
					case "SHUTDOWN" ->{
						for (var key: selector.keys()){
							silentlyClose(key);
						}
						try{
							serverSocketChannel.close();
							selector.close();
						} catch (IOException e) {
							logger.info("shutdown");
							break;
						}
						Thread.currentThread().interrupt();
					}
					case "SHUTDOWNNOW"->{
						try{
							serverSocketChannel.close();
							selector.close();
						} catch (IOException e) {
							logger.info("shutdown");
							break;
						}
						Thread.currentThread().interrupt();
					}
				}
			}
		}).start();
    }

    public void launch() throws IOException {
    	serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while(!Thread.interrupted()) {
			printKeys(); // for debug
			try {
				selector.select(this::treatKey);
			}catch (UncheckedIOException e) {
				throw e.getCause();
			}
		}
    }

	private void treatKey(SelectionKey key) {
		// TODO
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			silentlyClose(key);
		}
	}

	private void doAccept(SelectionKey key) throws IOException {
		var client=serverSocketChannel.accept();
		if(client==null){
			logger.warning("selector gave bad key");
			return;
		}
		client.configureBlocking(false);
		var clientKey=client.register(selector,SelectionKey.OP_READ);
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

    private static void usage(){
        System.out.println("Usage : ServerNonBlockingDupCleaner port");
    }
    
    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length!=1){
            usage();
            return;
        }
        new ServerNonBlockingDupCleaner(Integer.parseInt(args[0])).launch();
    }

	/***
	 ***  Methods below are here to help understanding the behavior of the selector
	 ***/

	private String interestOpsToString(SelectionKey key){
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps&SelectionKey.OP_ACCEPT)!=0) list.add("OP_ACCEPT");
		if ((interestOps&SelectionKey.OP_READ)!=0) list.add("OP_READ");
		if ((interestOps&SelectionKey.OP_WRITE)!=0) list.add("OP_WRITE");
		return String.join("|",list);
	}

	public void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet){
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : "+ interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client "+ remoteAddressToString(sc) +" : "+ interestOpsToString(key));
			}
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e){
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
		return String.join(" and ",list);
	}
}
