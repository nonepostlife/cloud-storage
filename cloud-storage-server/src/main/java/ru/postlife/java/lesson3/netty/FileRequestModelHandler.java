package ru.postlife.java.lesson3.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileModel;
import ru.postlife.java.model.FileRequestModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileRequestModelHandler extends SimpleChannelInboundHandler<FileRequestModel> {

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
    protected void channelRead0(ChannelHandlerContext ctx, FileRequestModel o) throws Exception {
        String fileName = o.getFileName();
        File myFile = serverDir.resolve(o.getFileName()).toFile();

        long fileLength = myFile.length();
        long batchCount = (fileLength + BUFFER_SIZE - 1) / BUFFER_SIZE;
        long i = 1;
        log.debug("upload file: {} ; batch count {} ", fileName, batchCount);

        try (FileInputStream fis = new FileInputStream(myFile)) {
            while (fis.available() > 0) {
                int read = fis.read(buf);

                FileModel model = new FileModel();
                model.setFileName(fileName);
                model.setFileLength(fileLength);
                model.setData(buf);
                model.setCountBatch(batchCount);
                model.setCurrentBatch(i++);
                model.setBatchLength(read);

                ctx.write(model);
                log.debug("send {} batch {}/{}", fileName, model.getCurrentBatch(), model.getCountBatch());
            }
        }
        ctx.flush();
        log.debug("upload file {} is success", fileName);
    }
}