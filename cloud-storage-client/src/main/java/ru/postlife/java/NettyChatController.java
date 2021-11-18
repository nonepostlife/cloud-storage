package ru.postlife.java;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.AuthModel;
import ru.postlife.java.model.FileListModel;
import ru.postlife.java.model.FileModel;
import ru.postlife.java.model.FileRequestModel;

import static java.nio.file.StandardWatchEventKinds.*;

@Slf4j
public class NettyChatController implements Initializable {

    private static final int BUFFER_SIZE = 1024;

    public ProgressBar progressBar;
    public ListView<String> clientView;
    public ListView<String> serverView;
    public TextField input;
    public TextField clientPath;
    public TextField serverPath;
    public Button clientBack;
    public Button serverBack;

    private Socket socket;
    private ObjectEncoderOutputStream os;
    private ObjectDecoderInputStream is;

    private byte[] buf;
    private Path clientDir;
    private Path currentClientDir;
    private Path currentServerDir;
    private AuthModel authModel;

    @SneakyThrows
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        buf = new byte[BUFFER_SIZE];
        authModel = new AuthModel();
        clientDir = Paths.get("cloud-storage-client", "client");
        if (!Files.exists(clientDir)) {
            Files.createDirectory(clientDir);
        }
        currentClientDir = Paths.get(clientDir.toString());
        clientPath.setEditable(false);
        serverPath.setEditable(false);
        clientView.getItems().clear();
        clientView.getItems().addAll(getFiles(currentClientDir));

        clientView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                String item = clientView.getSelectionModel().getSelectedItem();
                if (!item.isEmpty()) {
                    Path newPath = currentClientDir.resolve(item);
                    if (newPath.toFile().exists()) {
                        if (newPath.toFile().isDirectory()) {
                            currentClientDir = newPath;
                            try {
                                clientView.getItems().clear();
                                clientView.getItems().addAll(getFiles(currentClientDir));
                            } catch (IOException e) {
                                log.error("e", e);
                            }
                            clientPath.setText(currentClientDir.subpath(2, currentClientDir.getNameCount()).toString());
                        }
                    }
                }
            }
        });

        clientBack.setOnMouseClicked(event -> {
            if (!currentClientDir.equals(clientDir)) {
                currentClientDir = currentClientDir.getParent();
                try {
                    clientView.getItems().clear();
                    clientView.getItems().addAll(getFiles(currentClientDir));
                } catch (IOException e) {
                    log.error("e", e);
                }
                if (currentClientDir.getNameCount() == 2) {
                    clientPath.setText("");
                } else {
                    clientPath.setText(currentClientDir.subpath(2, currentClientDir.getNameCount()).toString());
                }
            }
        });

        clientView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> param) {
                ListCell<String> listCell = new ListCell<String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(item);
                    }
                };
                listCell.setOnDragDetected((MouseEvent event) ->
                {
                    System.out.println("listcell setOnDragDetected");
                    Dragboard db = listCell.startDragAndDrop(TransferMode.COPY);
                    ClipboardContent content = new ClipboardContent();
                    if (listCell.getItem() != null) {
                        content.putString(listCell.getItem());
                        db.setContent(content);
                        System.out.println(db.getString());
                        event.consume();
                    }
                });
                return listCell;
            }
        });

//        serverView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
//            @Override
//            public ListCell<String> call(ListView<String> param) {
//                ListCell<String> listCell = new ListCell<String>() {
//                    @Override
//                    protected void updateItem(String item, boolean empty) {
//                        super.updateItem(item, empty);
//                        if (item != null) {
//                            setText(item);
//                        }
//                    }
//                };
//                listCell.setOnDragOver((DragEvent event) ->
//                {
//                    Dragboard db = event.getDragboard();
//                    if (db.hasString()) {
//                        event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
//                    }
//                    event.consume();
//                });
//                listCell.setOnDragDropped((DragEvent event) ->
//                {
//                    System.out.println("treeCell.setOnDragDropped");
//                    Dragboard db = event.getDragboard();
//                    boolean success = false;
//                    if (db.hasString()) {
//                        System.out.println("Dropped: " + db.getString());
//                        try {
//                            sendFile(db.getString());
//                        } catch (IOException e) {
//                            log.error("e", e);
//                        }
//                        success = true;
//                    }
//                    event.setDropCompleted(success);
//                    event.consume();
//                });
//                return listCell;
//            }
//        });

        WatchService watchService = FileSystems.getDefault().newWatchService();
        runAsync(watchService);
        clientDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

        progressBar.setProgress(0);
    }

    private List<String> getFiles(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private void read() {
        while (true) {
            if (socket != null && !socket.isClosed()) {
                Object obj = is.readObject();
                log.debug("receive object {}", obj);

                if (obj.getClass() == AuthModel.class) {
                    authModel = (AuthModel) obj;
                    if (authModel.isAuth()) {
                        log.debug("user {} is success auth", authModel.getLogin());
                        showInformStringMessage(authModel.getResponse());

                        List<String> list = new ArrayList<>();
                        FileListModel files = new FileListModel();
                        files.setFiles(list);
                        files.setOwner(authModel.getLogin());
                        os.writeObject(files);
                        os.flush();
                    } else {
                        showErrorStringMessage(authModel.getResponse());
                    }
                    continue;
                }
                if (obj.getClass() == FileListModel.class) {
                    FileListModel fileListModel = (FileListModel) obj;
                    List<String> files = fileListModel.getFiles();
                    log.debug("files on server: {}", files);
                    Platform.runLater(() -> {
                        serverView.getItems().clear();
                        serverView.getItems().addAll(files);
                        Path path = Paths.get(fileListModel.getPath());
                        if (path.getNameCount() == 3) {
                            serverPath.setText("");
                        } else {
                            serverPath.setText(path.subpath(3, path.getNameCount()).toString());
                        }
                    });
                    continue;
                }
                if (obj.getClass() == FileModel.class) {
                    FileModel model = (FileModel) obj;
                    Path file = clientDir.resolve(model.getFileName());
                    String fileName = model.getFileName();

                    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                        log.debug("try download file: {}", fileName);
                        log.debug("open stream for receive file  \"{}\"", fileName);

                        while (true) {
                            fos.write(model.getData(), 0, model.getBatchLength());
                            log.debug("received: {} batch {}/{}", fileName, model.getCurrentBatch(), model.getCountBatch());
                            if (model.getCurrentBatch() == model.getCountBatch()) {
                                //fos.write(model.getData(), 0, model.getBatchLength());
                                log.debug("received: {} batch {}/{}", fileName, model.getCurrentBatch(), model.getCountBatch());
                                break;
                            }
                            model = (FileModel) is.readObject();
                        }
                        log.debug("close stream for receive file \"{}\"", fileName);
                        log.debug("download file {} is success", fileName);
                    } catch (Exception e) {
                        log.error("e", e);
                    }
                    Platform.runLater(() -> {
                        try {
                            clientView.getItems().clear();
                            clientView.getItems().addAll(getFiles(file.getParent()));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    continue;
                }
                if (obj.getClass() == String.class) {
                    String msg = (String) obj;
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
        }
    }

    public void send(ActionEvent actionEvent) throws IOException {
        os.writeObject(input.getText().trim());
        os.flush();
    }

    public void sendFile(ActionEvent actionEvent) throws IOException {
        sendFile(clientView.getSelectionModel().getSelectedItem());
    }

    public void sendFile(String fileName) throws IOException {
        Path myFile = currentClientDir.resolve(fileName);
        log.debug("try send file - {}", myFile.toFile());
        if (!myFile.toFile().exists()) {
            log.error("{} not exists", fileName);
            return;
        }
        if (myFile.toFile().isDirectory()) {
            log.error("{} is not file", fileName);
            return;
        }

        long fileLength = myFile.toFile().length();
        long batchCount = (fileLength + BUFFER_SIZE - 1) / BUFFER_SIZE;
        long i = 1;
        log.debug("upload file: {} ; batch count {} ", fileName, batchCount);

        try (FileInputStream fis = new FileInputStream(myFile.toFile())) {
            while (fis.available() > 0) {
                int read = fis.read(buf);

                FileModel model = new FileModel();
                model.setFileName(myFile.subpath(2, myFile.getNameCount()).toString());
                model.setOwner(authModel.getLogin());
                model.setData(buf);
                model.setCountBatch(batchCount);
                model.setCurrentBatch(i++);
                model.setBatchLength(read);

                os.writeObject(model);
                log.debug("send {} batch {}/{}", fileName, model.getCurrentBatch(), model.getCountBatch());

                double current = (double) i / batchCount;
                progressBar.setProgress(current);
            }
        }
        os.flush();
    }

    public void upload(ActionEvent actionEvent) throws IOException {
        upload(serverView.getSelectionModel().getSelectedItem());
    }

    public void upload(String fileName) throws IOException {
        FileRequestModel requestModel = new FileRequestModel();
        requestModel.setFileName(fileName);
        requestModel.setFilePath(serverPath.getText());
        requestModel.setOwner(authModel.getLogin());
        os.writeObject(requestModel);
        os.flush();
    }

    private void runAsync(WatchService watchService) {
        Thread thread = new Thread(() -> {
            System.out.println("Watch service starts listening");
            try {
                while (true) {
                    WatchKey watchKey = watchService.take();
                    List<WatchEvent<?>> events = watchKey.pollEvents();
                    for (WatchEvent<?> event : events) {
                        System.out.println(event.kind() + " " + event.context());
                        if (event.kind() == ENTRY_DELETE) {
                            Platform.runLater(() -> {
                                try {
                                    clientView.getItems().clear();
                                    clientView.getItems().addAll(getFiles(clientDir));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                    watchKey.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void openConnectDialog(ActionEvent actionEvent) {
        if (socket == null) {
            try {
                socket = new Socket("localhost", 8189);
                os = new ObjectEncoderOutputStream(socket.getOutputStream());
                is = new ObjectDecoderInputStream(socket.getInputStream());
                Thread thread = new Thread(this::read);
                thread.setDaemon(true);
                thread.start();
            } catch (Exception e) {
                log.error("e", e);
                showErrorStringMessage("Unable to connect to server!");
            }
        }
        if (socket != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("auth.fxml"));
                Parent parent = loader.load();
                AuthController authController = loader.<AuthController>getController();
                authController.setAuthModel(authModel);

                Scene scene = new Scene(parent, 300, 200);
                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL);
                stage.setScene(scene);
                stage.showAndWait();

                log.debug("send request on auth for user " + authModel.getLogin());
                os.writeObject(authModel);
                os.flush();
            } catch (Exception e) {
                log.error("e", e);
            }
        }
    }

    private void showErrorStringMessage(String message) {
        Platform.runLater(() -> {
            new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
        });
    }

    private void showInformStringMessage(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Info");
            alert.setHeaderText(message);
            alert.setContentText(String.format("Welcome, %s %s!\nWe wish you pleasant work in our application!", authModel.getFirstname(), authModel.getLastname()));
            alert.showAndWait();
        });
    }

    private void closeConnectionAndResources() {
        try {
            if (is != null) {
                is.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (os != null) {
                os.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect(ActionEvent actionEvent) {
        closeConnectionAndResources();
    }
}
