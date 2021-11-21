package ru.postlife.java.storage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileModel;
import ru.postlife.java.model.FileRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileRequestModelHandler extends SimpleChannelInboundHandler<FileRequest> {

    private static int BUFFER_SIZE = 1024;
    private byte[] buf;
    private static int counter = 0;

    private Path serverDir;
    private OutputStream fos;

    public FileRequestModelHandler() {
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

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FileRequest o) throws Exception {
        String owner = o.getOwner();
        String filePath = o.getFilePath();
        String fileName = o.getFileName();
        File myFile = serverDir.resolve(owner).resolve(filePath).resolve(fileName).toFile();

        long fileLength = myFile.length();
        long batchCount = (fileLength + BUFFER_SIZE - 1) / BUFFER_SIZE;
        long i = 1;
        log.debug("try to upload file:{}; length:{}; batch count:{} ", myFile, fileLength, batchCount);

        Path path = Paths.get(filePath, fileName);

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
                log.debug("send file:{}; batch:{}/{}", myFile, model.getCurrentBatch(), model.getCountBatch());
            }
        }
        ctx.flush();
        log.debug("upload file {} is success", myFile);
    }
}