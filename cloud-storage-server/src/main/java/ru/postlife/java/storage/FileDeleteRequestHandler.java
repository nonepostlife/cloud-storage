package ru.postlife.java.storage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileDeleteRequest;
import ru.postlife.java.model.FileList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FileDeleteRequestHandler extends SimpleChannelInboundHandler<FileDeleteRequest> {

    private final Path serverDir;

    public FileDeleteRequestHandler() {
        serverDir = Paths.get("cloud-storage-server", "server");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("e", cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.debug("Client delete file...");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FileDeleteRequest o) throws IOException {
        String owner = o.getOwner();
        String filePath = o.getFilePath();
        String fileName = o.getFileName();
        Path file = serverDir.resolve(owner).resolve(filePath).resolve(fileName);

        if (file.toFile().isFile()) {
            if (Files.deleteIfExists(file))
                log.debug("user:{} delete file:\"{}\" from path:\"{}\"", owner, fileName, filePath);
        } else {
            Files.walk(file)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.debug("user:{} delete directory:\"{}\" from path:\"{}\"", owner, fileName, filePath);
        }

        // отправка списка файлов
        List<String> filenames = Files.list(file.getParent()).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
        FileList model = new FileList();
        model.setFilenames(filenames);
        model.setOwner(owner);

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
        log.debug("send list files to user:{}; from path:\"{}\"; filenames:{}; filesMap:{}", o.getOwner(), filePath, filenames, filesMap);
    }
}
