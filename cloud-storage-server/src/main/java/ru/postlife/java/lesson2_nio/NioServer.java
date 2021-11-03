package ru.postlife.java.lesson2_nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NioServer {

    private ByteBuffer buf;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    public NioServer() {
        buf = ByteBuffer.allocate(10);
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(8189));
            serverChannel.configureBlocking(false);
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            log.debug("Server started...");
            while (serverChannel.isOpen()) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                for (SelectionKey key : keys) {
                    if (key.isAcceptable()) {
                        handleAccept();
                    }
                    if (key.isReadable()) {
                        handleRead(key);
                    }
                }
                keys.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        StringBuilder sb = new StringBuilder("From server: ");
        while (true) {
            int read = channel.read(buf);
            if (read == -1) {
                channel.close();
                return;
            }
            if (read == 0) {
                break;
            }
            if (read > 0) {
                buf.flip();
                while (buf.hasRemaining()) {
                    sb.append((char) buf.get());
                }
                buf.clear();
            }
        }
        //     ррпр  пр   \n\r
        // String msg = "Heee   ".trim();
        log.debug("Received: {}", sb);
        channel.write(ByteBuffer.wrap(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private void handleAccept() throws IOException {
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        // send Welcome message for user
        log.debug("Client connected...");
    }


    public static void main(String[] args) {
        new NioServer();
    }

}
