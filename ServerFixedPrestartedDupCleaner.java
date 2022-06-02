package ex2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerFixedPrestartedDupCleaner {
	private static final Logger logger =
			Logger.getLogger(ServerFixedPrestartedDupCleaner.class.getName());
	// TODO
	private final int nbThreads;
	private final ServerSocketChannel serverSocketChannel;
	public ServerFixedPrestartedDupCleaner(int port, int nbThreads) throws IOException {
		// Initialise server based on given arguments
		this.serverSocketChannel=ServerSocketChannel.open();;
		serverSocketChannel.bind(new InetSocketAddress(port));
		if(nbThreads<1) throw new IllegalArgumentException();
		this.nbThreads=nbThreads;
		System.out.println("Started server on port : "+port+ " with "+ nbThreads+ " threads");
	}

	public void launch() {
        // Run the server, accept clients
		System.out.println("Now accepting up to "+ nbThreads+ " clients at the same time");
		for(int i=0;i<nbThreads;i++){
			new Thread(()->{
				SocketChannel client;
				while(!Thread.interrupted()){
					try{
						client=serverSocketChannel.accept();
					} catch (IOException e) {
						logger.log(Level.SEVERE,"Panda attack");
						return;
					}
					try{
						System.out.println("Accepting client "+client);
						serve(client);
					} catch (IOException e) {
						logger.info("The client is not respecting the protocol");
					}finally {
						silentlyClose(client);
					}
				}
			}).start();
		}
	}
	private void silentlyClose(SocketChannel client){
		try {
			if(client!=null)
				client.close();
		} catch (IOException e) {}
	}

	private void serve(SocketChannel client) throws IOException {
		// Do what the server is supposed to do
		var bufferIn=ByteBuffer.allocate(Byte.BYTES);
		var bufferOut=ByteBuffer.allocate(Byte.BYTES);
		byte old=0;
		while(true){
			bufferIn.clear();
			bufferOut.clear();
			if(!readFully(client,bufferIn)) return;
			var received=bufferIn.flip().get();
			if(old!=received){
				old=received;
				bufferOut.put(received);
			}
			client.write(bufferOut.flip());
		}
	}
	private static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
		while(true) {
			if (sc.read(buffer) == -1) return false;
			if (!buffer.hasRemaining()) return true;
		}
	}


	public static void main(String[] args) throws NumberFormatException, IOException {
		if(args.length != 2) {
			System.out.println("usage: java fr.upem.net.tcp.exam1819.ServerFixedPrestartedDupCleaner port maxThreads");
			return;
		}
		ServerFixedPrestartedDupCleaner server =
				new ServerFixedPrestartedDupCleaner(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
		server.launch();
	}
}