package ru.postlife.java.lesson2_nio;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NioServer {

    private Path root;
    private Path current;

    private ByteBuffer buf;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    public NioServer() {


        buf = ByteBuffer.allocate(10);
        try {
            Path parent = Paths.get("");
            root = Paths.get("cloud-storage-server", "server", "root");
            current = Paths.get(root.toString());
            for (Path p : root) {
                Path cur = parent.resolve(p);
                if (!Files.exists(cur)) {
                    Files.createDirectory(cur);
                }
                parent = cur;
            }
            log.debug("root path is {}", root.toString());

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
        StringBuilder in = new StringBuilder();
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
                    in.append((char) buf.get());
                }
                buf.clear();
            }
        }

        String msg = in.toString().trim();
        StringBuilder out = new StringBuilder("");

        if (msg.startsWith("ls")) {
            lsFunc(current, out, msg);
        } else if (msg.startsWith("touch")) {
            touchFunc(current, out, msg);
        } else if (msg.equals("pwd")) {
            pwdFunc(current, out);
        } else if (msg.startsWith("cat")) {
            catFunc(current, out, msg);
        } else if (msg.startsWith("mkdir")) {
            mkdirFunc(current, out, msg);
        } else if (msg.startsWith("cd")) {
            cdFunc(current, out, msg);
        } else {
            out.append("unknown command").append("\r\n");
        }

        log.debug("Received: {}", msg);
        out.insert(0, "From server: ");
        log.debug("{}", out);
        channel.write(ByteBuffer.wrap(out.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private void cdFunc(Path current, StringBuilder sb, String msg) {
        String[] tokens = msg.split("\\s+");
        if (tokens.length == 1) {
            current = Paths.get(root.toString());
        } else if (tokens.length == 2) {
            String path = tokens[1];
            if (path.equals("~")) {
                current = Paths.get(root.toString());
            } else {
                Path old = Paths.get(current.toString());

                String[] paths = path.split("\\\\");
                for (String s : paths) {
                    if (s.equals("..")) {
                        if (current.getParent().toString().equals("root")) {
                            sb.append("null").append("\r\n");
                            current = old;
                            return;
                        } else {
                            current = current.getParent();
                        }
                    } else {
                        if (Files.exists(current.resolve(s))) {
                            current = current.resolve(s);
                        } else {
                            sb.append("null").append("\r\n");
                            current = old;
                            return;
                        }
                    }
                }
            }
        }
    }

    private void mkdirFunc(Path current, StringBuilder sb, String msg) throws IOException {
        String[] tokens = msg.split("\\s+");
        if (tokens.length == 1) {
            sb.append("directory is not specified").append("\r\n");
        } else {
            for (int i = 1; i < tokens.length; i++) {
                String dir = tokens[i];
                Path path = current.resolve(dir);
                if (Files.exists(path)) {
                    sb.append("directory ").append(tokens[i]).append(" already exist");
                } else {
                    Files.createDirectories(path);
                    sb.append("directory ").append(tokens[i]).append(" created");
                }
                sb.append("\r\n");
            }
        }
    }

    private void catFunc(Path current, StringBuilder sb, String msg) throws IOException {
        String[] tokens = msg.split("\\s+");
        if (tokens.length == 1) {
            sb.append("file not specified").append("\r\n");
        } else {
            for (int i = 1; i < tokens.length; i++) {
                String file = tokens[i];
                Path path = current.resolve(file);
                if (!Files.exists(path)) {
                    sb.append("\r\n");
                    sb.append("file ").append(tokens[i]).append(" is not exist");
                } else {
                    sb.append("\r\n");
                    readFile(path, sb);
                }
            }
            sb.append("\r\n");
        }
    }

    private void readFile(Path path, StringBuilder sb) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        SeekableByteChannel channel = Files.newByteChannel(path);

        byte[] result = new byte[(int) channel.size()];
        int pos = 0;
        while (true) {
            int read = channel.read(buffer);
            if (read <= 0) {
                break;
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                result[pos] = buffer.get();
                pos++;
            }
            buffer.clear();
        }
        if (result.length != 0)
            sb.append(new String(result, StandardCharsets.UTF_8));
    }

    private String pwdFunc(Path current, StringBuilder sb) {
        String[] tokens = current.toString().split("\\\\");
        sb.append("\\");
        for (int i = 3; i < tokens.length; i++) {
            sb.append(tokens[i]).append("\\");
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private void lsFunc(Path current, StringBuilder sb, String msg) throws IOException {
        String[] tokens = msg.split("\\s+");
        if (tokens.length == 1) {
            sb.append("\r\n");
            Files.walkFileTree(current, new HashSet<>(), 1, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    File file = path.toFile();
                    if (file.isDirectory()) {
                        sb.append("dir: ");
                    }
                    sb.append(file.getName()).append("\r\n");
                    return super.visitFile(path, attrs);
                }
            });
        } else if (tokens.length == 2) {
            String dir = tokens[1];
            Path path = current.resolve(dir);
            if (Files.exists(path) && Files.isDirectory(path)) {
                sb.append("\r\n");
                Files.walkFileTree(path, new HashSet<>(), 1, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        File file = path.toFile();
                        if (file.isDirectory()) {
                            sb.append("dir: ");
                        }
                        sb.append(file.getName()).append("\r\n");
                        return super.visitFile(path, attrs);
                    }
                });
            } else {
                sb.append("unknown path").append("\r\n");
            }
        } else if (tokens.length > 2) {
            sb.append("too many parameters");
        }
    }

    private void touchFunc(Path current, StringBuilder sb, String msg) throws IOException {
        String[] tokens = msg.split("\\s+");
        if (tokens.length < 2) {
            sb.append("file name not specified");
        } else {
            for (int i = 1; i < tokens.length; i++) {
                if (createFile(current.resolve(tokens[i])) < 0) {
                    sb.append("file ").append(tokens[i]).append(" already exist");
                } else {
                    sb.append("file ").append(tokens[i]).append(" created");
                }
                sb.append("\r\n");
            }
        }
    }

    public static int createFile(Path storagePath) throws IOException {
        Files.createDirectories(storagePath.getParent());
        if (Files.exists(storagePath)) {
            return -1;
        }
        Files.createFile(storagePath);
        return 1;
    }

    private void handleAccept() throws IOException {
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        // send Welcome message for user
        String hello = "Welcome to server\r\n";
        channel.write(ByteBuffer.wrap(hello.getBytes(StandardCharsets.UTF_8)));
        log.debug("Client connected...");
    }

    public static void main(String[] args) {
        new NioServer();
    }
}
