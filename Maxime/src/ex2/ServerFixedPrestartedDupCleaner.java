package ex2;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class ServerFixedPrestartedDupCleaner {
	private static final Logger logger = 
			Logger.getLogger(ServerFixedPrestartedDupCleaner.class.getName());
	// TODO
	
	public ServerFixedPrestartedDupCleaner(int port, int nbThreads) throws IOException {
		// TODO
	}

	public void launch() {
        // TODO
	}

	private void serve(SocketChannel client) throws IOException {
		// TODO
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