package ru.postlife.java.storage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileListModel;
import ru.postlife.java.model.FileModel;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class FileModelHandler extends SimpleChannelInboundHandler<FileModel> {

    private static int BUFFER_SIZE = 1024;
    private byte[] buf;
    private static int counter = 0;

    private Path serverDir;
    private OutputStream fos;

    public FileModelHandler() {
        serverDir = Paths.get("cloud-storage-server", "server");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("e", cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Client upload file...");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FileModel o) throws Exception {
        String fileName = o.getFileName();
        Path file = serverDir.resolve(fileName);
//        if (!Files.exists(file.getParent())) {
//            Files.createDirectory(file.getParent());
//        }

        if(o.getCurrentBatch() == 1) {
            fos = new FileOutputStream(file.toFile());
            log.debug("download file: {}", fileName);
            log.debug("open stream for receive file  \"{}\"", fileName);
        }

        fos.write(o.getData(), 0, o.getBatchLength());
        log.debug("received: {} batch {}/{}", fileName, o.getCurrentBatch(), o.getCountBatch());

        if (o.getCurrentBatch() == o.getCountBatch()) {
            fos.close();
            log.debug("close stream for receive file \"{}\"", fileName);

            List<String> files = Files.list(serverDir).map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
            ctx.writeAndFlush(new FileListModel(files));
            log.debug("send list files: {}", files);
        }
    }
}
