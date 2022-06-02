# TCP_Servers_JAVA
blocking and non blocking TCP servers and clients in java


Subject: http://www-igm.univ-mlv.fr/~carayol/coursprogreseauINFO2/tds/examen-tcp-2019.html


# Useful functions to remember : 

## Launch 
### Blocking 
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
	
### Non Blocking
```java
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
```

## TreatKey
```java
private void treatKey(SelectionKey key) {
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
```

## doAccept
```java
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
```

## SilentlyClose
```java
private void silentlyClose(SelectionKey key) {
	var sc = (Channel) key.channel();
	try {
		sc.close();
	} catch (IOException e) {
		// ignore exception
	}
}
```
For the "socket channel version" just pass it as an arg and do the same steps

## UpdateInterestOps
```java
private void updateInterestOps(){
	var ops=0;
	if(bufferIn.hasRemaining() && !closed) ops |= SelectionKey.OP_READ;
	if(bufferOut.position()!=0) ops|=SelectionKey.OP_WRITE;
	if(ops==0){
		silentlyClose(key);
		return;
	}
	key.interestOps(ops);
}
```
## DoRead and DoWrite
```java
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
```
## Process
```java
private void process(){
	bufferOut.clear();
	bufferIn.flip();
	// Do what the server is supposed to do with packets he receives
	bufferIn.compact();
}
```
