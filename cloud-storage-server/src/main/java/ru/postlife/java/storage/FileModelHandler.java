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
        String filePath = o.getFilePath();
        Path path = serverDir.resolve(o.getOwner());
        Path file = path.resolve(filePath);
        if (!Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
        if (o.getCurrentBatch() == 1) {
            fos = new FileOutputStream(file.toFile());
            log.debug("download file:{}", filePath);
            log.debug("open stream for receive file:{}", filePath);
        }

        fos.write(o.getData(), 0, o.getBatchLength());
        log.debug("received file:{}; batch:{}/{}", filePath, o.getCurrentBatch(), o.getCountBatch());

        if (o.getCurrentBatch() == o.getCountBatch()) {
            fos.close();
            log.debug("close stream for receive file:{}", filePath);

            // отправка списка файлов
            List<String> filenames = Files.list(file.getParent()).map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
            FileList model = new FileList();
            model.setFilenames(filenames);
            model.setOwner(o.getOwner());

            // 2 способ
            Map<String, Boolean> filesMap = new HashMap<>();
            for (String filename : filenames) {
                File f = file.getParent().resolve(filename).toFile();
                filesMap.put(filename, f.isDirectory());
            }
            model.setFilesInfoMap(filesMap);

            if (file.getParent().getNameCount() == 3) {
                model.setPath("");
            } else {
                model.setPath(file.subpath(3, file.getNameCount() - 1).toString());
            }

            ctx.writeAndFlush(model);
            log.debug("send list files to user:{}; from path:{}; filenames:{}; filesMap:{}", o.getOwner(), filePath, filenames, filesMap);
        }
    }
}
