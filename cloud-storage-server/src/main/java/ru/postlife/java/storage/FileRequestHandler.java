package ru.postlife.java.storage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileModel;
import ru.postlife.java.model.FileRequest;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileRequestHandler extends SimpleChannelInboundHandler<FileRequest> {

    private static final int BUFFER_SIZE = 1024;
    private byte[] buf;

    private Path serverDir;

    public FileRequestHandler() {
        buf = new byte[BUFFER_SIZE];
        serverDir = Paths.get("cloud-storage-server", "server");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("e", cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Client request file...");
    }

    @SneakyThrows
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FileRequest o) {
        String owner = o.getOwner();
        String filePath = o.getFilePath();
        String fileName = o.getFileName();
        File myFile = serverDir.resolve(owner).resolve(filePath).resolve(fileName).toFile();

        if (myFile.isDirectory()) {
            // TODO: 24.11.2021 добавить отправку всех файлов из этой папки
            Files.walk(myFile.toPath())
                    .forEach(path -> {
                        System.out.println(path);
                        sendFileToClient(ctx, path.toFile(), owner);
                    });
        } else {
            sendFileToClient(ctx, myFile, owner);
        }
    }

    @SneakyThrows
    private void sendFileToClient(ChannelHandlerContext ctx, File myFile, String owner) {
        if (myFile.isDirectory()) {
            return;
        }
        long fileLength = myFile.length();
        long batchCount = (fileLength + BUFFER_SIZE - 1) / BUFFER_SIZE;
        long i = 1;
        log.debug("try to upload file:{}; length:{}; batch count:{} ", myFile, fileLength, batchCount);


        Path path = Paths.get(myFile.toPath().getParent().subpath(3, myFile.toPath().getParent().getNameCount()).toString(), myFile.getName());

        try (FileInputStream fis = new FileInputStream(myFile)) {
            while (fis.available() > 0) {
                int read = fis.read(buf);

                FileModel model = new FileModel();
                model.setOwner(owner);
                model.setFilePath(path.toString());
                model.setData(buf);
                model.setCountBatch(batchCount);
                model.setCurrentBatch(i++);
                model.setBatchLength(read);

                ctx.write(model);
                log.debug("send file:{}; from path:{} batch:{}/{}", myFile.getName(), path.getParent(), model.getCurrentBatch(), model.getCountBatch());
            }
        }
        ctx.flush();
        log.debug("upload file:{} from path:{} is success", myFile, path.getParent());
    }
}