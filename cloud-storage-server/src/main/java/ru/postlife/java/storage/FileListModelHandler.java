package ru.postlife.java.storage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class FileListModelHandler extends SimpleChannelInboundHandler<FileList> {

    private final Path serverDir;

    public FileListModelHandler() {
        serverDir = Paths.get("cloud-storage-server", "server");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("e", cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Client request files from server...");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FileList o) throws Exception {
        if (o.getFiles().isEmpty()) {
            Path path = serverDir.resolve(o.getOwner());
            if (!Files.exists(serverDir.resolve(o.getOwner()))) {
                Files.createDirectories(path);
                ctx.writeAndFlush(o);
                return;
            }
            path = path.resolve(o.getPath());
            log.debug("user:{} request list of files from path:{}", o.getOwner(), path);
            if (path.toFile().exists()) {

                List<String> files = Files.list(path).map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
                o.setFiles(files);
                //if(o.getPath().equals("")) {
                    if (path.getNameCount() == 3) {
                        o.setPath("");
                    } else {
                        o.setPath(path.subpath(3, path.getNameCount()).toString());
                    }
                //}
                ctx.writeAndFlush(o);
                log.debug("send list files to user:{}, from path:{}, files:{}", o.getOwner(), path, files);
            } else {
                ctx.writeAndFlush(String.format("Path %s for user %s not exist", o.getPath(), o.getOwner()));
                log.debug("Path:{} for user:{} not exist", o.getPath(), o.getOwner());
            }
        }
    }
}
