package ru.postlife.java.storage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FileListHandler extends SimpleChannelInboundHandler<FileList> {

    private final Path serverDir;

    public FileListHandler() {
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
        if (o.getFilenames().isEmpty()) {
            Path path = serverDir.resolve(o.getOwner());
            if (!Files.exists(serverDir.resolve(o.getOwner()))) {
                Files.createDirectories(path);
                ctx.writeAndFlush(o);
                return;
            }
            path = path.resolve(o.getPath());
            log.debug("user:{} request list of files from path:{}", o.getOwner(), path);
            if (path.toFile().exists()) {

                List<String> filenames = Files.list(path).map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
                o.setFilenames(filenames);

                // 2 способ
                Map<String, Boolean> filesMap = new HashMap<>();
                for (String filename : filenames) {
                    File file = path.resolve(filename).toFile();
                    filesMap.put(filename, file.isDirectory());
                }
                o.setFilesInfoMap(filesMap);

                if (path.getNameCount() == 3) {
                    o.setPath("");
                } else {
                    o.setPath(path.subpath(3, path.getNameCount()).toString());
                }

                ctx.writeAndFlush(o);
                log.debug("send list files to user:{}, from path:{}, files:{}", o.getOwner(), path, filenames);
            } else {
                o.setFilesInfoMap(new HashMap<>());
                if (path.getNameCount() == 3) {
                    o.setPath("");
                } else {
                    o.setPath(path.subpath(3, path.getNameCount()).toString());
                }

                ctx.writeAndFlush(o);
                log.debug("send list files to user:{}, from path:{}, files:{}", o.getOwner(), path, o.getFilenames());
            }
        }
    }
}
