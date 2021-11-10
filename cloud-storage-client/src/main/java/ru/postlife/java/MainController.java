package ru.postlife.java;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainController implements Initializable {

    public final static int SOCKET_PORT = 9190;
    public final static String SERVER = "127.0.0.1";

    private Path clientDir;
    public ListView<String> clientView;
    public ListView<String> serverView;
    public TextField input;

    private DataInputStream is;
    private DataOutputStream os;
    private DataInputStream fis;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            clientDir = Paths.get("cloud-storage-client", "client");
            if (!Files.exists(clientDir)) {
                Files.createDirectory(clientDir);
            }
            input.setEditable(false);

            clientView.getItems().clear();
            clientView.getItems().addAll(getFiles(clientDir));
            clientView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String item = clientView.getSelectionModel().getSelectedItem();
                    input.setText(item);
                    input.requestFocus();
                }
            });
            Socket socket = new Socket(SERVER, SOCKET_PORT);
            is = new DataInputStream(socket.getInputStream());
            os = new DataOutputStream(socket.getOutputStream());
            Thread readThread = new Thread(this::read);
            readThread.setDaemon(true);
            readThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getFiles(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

    private void read() {
        try {
            while (true) {
                String msg = is.readUTF();
                log.debug("Received: {}", msg);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Info");
                    alert.setHeaderText("Server response");
                    alert.setContentText(msg);
                    alert.showAndWait().ifPresent(rs -> {
                        if (rs == ButtonType.OK) {
                            System.out.println("Pressed OK.");
                        }
                    });
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(ActionEvent actionEvent) throws IOException {
        String text = input.getText();
        os.writeUTF(text);
        os.flush();
        input.clear();
        // TODO: 28.10.2021 Передать файл на сервер
    }

    public void sendFile(ActionEvent actionEvent) throws IOException {
        String fileName = input.getText();
        File myFile = new File(clientDir + "/" + fileName);
        log.debug("try send file - {}", myFile.getAbsolutePath());
        if (!myFile.exists()) {
            log.error("{} not exists", fileName);
            return;
        }
        if (myFile.isDirectory()) {
            log.error("{} is not file", fileName);
            return;
        }

        fis = new DataInputStream(new FileInputStream(clientDir + "\\" + fileName));
        os.writeUTF(fileName);
        os.flush();

        int fileLength = (int) myFile.length();
        byte[] myByteArray = new byte[fileLength];
        os.writeInt(fileLength);
        os.flush();

        fis.readFully(myByteArray);
        os.write(myByteArray, 0, myByteArray.length);
        os.flush();
        log.debug("{} is send to server", fileName);
        input.clear();
    }
}
