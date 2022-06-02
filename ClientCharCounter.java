package ex1;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ClientCharCounter {
	public static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
		while(true){
			if(sc.read(buffer)==-1) return false;
			if(!buffer.hasRemaining()) return true;
		}
	}
	public static boolean packetMaker(ByteBuffer buffer,String line){
		var encoded=StandardCharsets.UTF_8.encode(line);
		if(encoded.remaining()>Short.MAX_VALUE) {
			System.out.println("encoded value bigger than max short value");
			return false;
		}
		buffer.putShort((short) encoded.remaining());
		buffer.put(encoded);
		return true;
	}
	public static String packetReader(SocketChannel sc, ByteBuffer buffer) throws IOException {
		buffer.flip();
		var size=buffer.getShort();
		var sb=new StringBuilder();
		sb.append(size).append(" characters found\n");
		buffer=ByteBuffer.allocate(Short.BYTES+Byte.BYTES);
		for(int i=0;i<size;i++){
			if(!readFully(sc,buffer)) return "";
			buffer.flip();
			var nbOcc=buffer.getShort();
			var sizeChar=buffer.get();
			var tmpBuff=ByteBuffer.allocate(sizeChar);
			if(readFully(sc,tmpBuff));
			sb.append(StandardCharsets.UTF_8.decode(tmpBuff.flip())).append(" : ").append(nbOcc).append("\n");
			tmpBuff.clear();
			buffer.clear();
		}
		return sb.toString();
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.out.println("usage: java fr.upem.net.tcp.exam1819.ClientCharCounter host port");
			return;
		}

		// TODO: vous pouvez coder le client dans le main sans crÃ©er de constructeur
		// creation of the socket (not connected)
		SocketChannel sc = SocketChannel.open();
		var serverAddress = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
		// connection to server
		sc.connect(serverAddress);
		System.out.println("Write something, or CTRL + C to stop");
		try(var scan=new Scanner(System.in)){
			while(scan.hasNextLine()){
				var buffer=ByteBuffer.allocate(Short.MAX_VALUE);
				var line=scan.nextLine();
				if(!packetMaker(buffer,line)) return;
				buffer.flip();
				sc.write(buffer);
				buffer.clear();
				buffer=ByteBuffer.allocate(Short.BYTES);
				if(readFully(sc,buffer)) {
					System.out.println("received :");
					System.out.println(packetReader(sc,buffer));
				}
				buffer.clear();
			}
		}
	}
}