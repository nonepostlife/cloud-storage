package ru.postlife.java.lesson1_io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Handler implements Runnable {

    private Path clientDir;
    private int fileLength;
    private String fileName;

    private static int counter = 0;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataInputStream is;
    private final DataOutputStream os;

    private DataOutputStream fos;

    private final String username;
    private boolean isRunning;

    public Handler(Socket socket) throws IOException {
        is = new DataInputStream(socket.getInputStream());
        os = new DataOutputStream(socket.getOutputStream());
        counter++;
        username = "User#" + counter;
        log.debug("Set nick: {} for new client", username);
        isRunning = true;

        clientDir = Paths.get("cloud-storage-server", username);
        if (!Files.exists(clientDir)) {
            Files.createDirectory(clientDir);
        }
    }
    private String getDate() {
        return formatter.format(LocalDateTime.now());
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    @Override
    public void run() {
        try {
            while (isRunning) {
                fileName = is.readUTF();
                String filePath = clientDir + "\\" + fileName;
                fos = new DataOutputStream(new FileOutputStream(filePath));
                fileLength = is.readInt();
                byte [] myByteArray  = new byte [fileLength];
                is.readFully(myByteArray);

                fos.write(myByteArray, 0, fileLength);
                fos.flush();

                String msg = String.format("Received file from %s: %s", username, filePath);
                log.debug(msg);
                String response = String.format("%s %s: %s", getDate(), username, msg);
                log.debug("Message for response: {}", response);
                os.writeUTF(response);
                os.flush();
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }
}
