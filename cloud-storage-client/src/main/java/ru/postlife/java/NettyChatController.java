package ru.postlife.java;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileModel;

@Slf4j
public class NettyChatController implements Initializable {

    private static final int BUFFER_SIZE = 1024;

    private byte [] buf;
    private Path clientDir;

    public ListView<String> clientView;
    public ListView<String> serverView;
    public TextField input;

    private ObjectEncoderOutputStream os;
    private ObjectDecoderInputStream is;

    @SneakyThrows
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        buf = new byte[BUFFER_SIZE];
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

        Socket socket = new Socket("localhost", 8189);
        os = new ObjectEncoderOutputStream(socket.getOutputStream());
        is = new ObjectDecoderInputStream(socket.getInputStream());
        Thread thread = new Thread(this::read);
        thread.setDaemon(true);
        thread.start();

    }

    private List<String> getFiles(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

//    @SneakyThrows
//    private void read() {
//        while (true) {
//            String msg = is.readUTF();
//            Platform.runLater(() -> list.getItems().add(msg));
//        }
//    }

    @SneakyThrows
    private void read() {
            while (true) {
                String msg = (String) is.readObject();
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
    }

    public void send(ActionEvent actionEvent) throws IOException {
        os.writeObject(input.getText());
        os.flush();
    }
}
