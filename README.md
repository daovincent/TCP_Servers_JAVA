# TCP_Servers_JAVA
blocking and non blocking TCP servers and clients in java


Subject: http://www-igm.univ-mlv.fr/~carayol/coursprogreseauINFO2/tds/examen-tcp-2019.html


Useful functions to remember : 

## Launch 
### Non blocking 
```java
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
        ```
