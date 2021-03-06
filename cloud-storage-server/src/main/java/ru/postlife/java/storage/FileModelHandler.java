package ru.postlife.java.storage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileList;
import ru.postlife.java.model.FileModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FileModelHandler extends SimpleChannelInboundHandler<FileModel> {

    private final Path serverDir;
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
        String filePath = o.getFilePath();
        Path path = serverDir.resolve(o.getOwner());
        Path file = path.resolve(filePath);
        if (!Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
        if (o.getCurrentBatch() == 1) {
            fos = new FileOutputStream(file.toFile());
            log.debug("download file:\"{}\"", filePath);
            log.debug("open stream for receive file:\"{}\"", filePath);
        }

        fos.write(o.getData(), 0, o.getBatchLength());
        log.debug("received file:\"{}\"; batch:{}/{}", filePath, o.getCurrentBatch(), o.getCountBatch());

        if (o.getCurrentBatch() == o.getCountBatch()) {
            fos.close();
            log.debug("close stream for receive file:\"{}\"", filePath);
            log.debug("download file:\"{}\" is successful", filePath);
        }
    }
}
