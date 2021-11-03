package ru.postlife.java.lesson2_nio;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;

public class NioExamples {

    public static void main(String[] args) throws IOException {
        // Path - путь к файлу
//        Path parent = Paths.get("");
        Path path = Paths.get("cloud-storage-nov-server", "server", "root");
//        for (Path p : path) {
//            Path cur = parent.resolve(p);
//            System.out.println(cur);
//            if (!Files.exists(cur)) {
//                Files.createDirectory(cur);
//            }
//            parent = cur;
//        }
        System.out.println(path.getParent());
        System.out.println(path.toAbsolutePath());
        System.out.println(path.toAbsolutePath().getParent());

        System.out.println(path.getFileName().toString());
        System.out.println(path.getFileSystem().provider());

//        WatchService watchService = FileSystems.getDefault().newWatchService();
//        runAsync(watchService);
//        path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

        // WRITE
        // abcdef
        // gt -> gtcdef
        Path helloPath = path.resolve("Hello.txt");

//        Files.write(
//                helloPath,
//                "gt".getBytes(StandardCharsets.UTF_8),
//                StandardOpenOption.APPEND
//        );

        Files.copy(
                helloPath,
                path.resolve("copy.txt"),
                StandardCopyOption.REPLACE_EXISTING
        );

        Path root = Paths.get("");
//        Files.walkFileTree(root, new HashSet<>(), 2, new SimpleFileVisitor<Path>() {
//            @Override
//            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
//                System.out.println(file);
//                return super.visitFile(file, attrs);
//            }
//        });
        Files.walk(root, 2)
                .forEach(System.out::println);
    }

    private static void runAsync(WatchService watchService) {
        new Thread(() -> {
            System.out.println("Watch service starts listening");
            try {
                while (true) {
                    WatchKey watchKey = watchService.take();
                    List<WatchEvent<?>> events = watchKey.pollEvents();
                    for (WatchEvent<?> event : events) {
                        System.out.println(event.kind() + " " + event.context());
                    }
                    watchKey.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
