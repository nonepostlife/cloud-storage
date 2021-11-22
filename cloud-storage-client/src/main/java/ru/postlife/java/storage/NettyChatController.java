package ru.postlife.java.storage;

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
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;


import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.*;

import static java.nio.file.StandardWatchEventKinds.*;

@Slf4j
public class NettyChatController implements Initializable {

    private final Image IMAGE_FOLDER = new Image(getClass().getResource("icons/folder.png").toString(), 16, 16, false, false);
    private final Image IMAGE_DOC = new Image(getClass().getResource("icons/doc.png").toString(), 16, 16, false, false);

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

    private List<FileInfo> currentServerFiles;
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

        //clientView.getItems().clear();
        //clientView.getItems().addAll(getFiles(currentClientDir));

        // переход в папку
        clientView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                String item = clientView.getSelectionModel().getSelectedItem();
                if (item != null && !item.isEmpty()) {
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
        serverView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                String item = serverView.getSelectionModel().getSelectedItem();
                if (item != null && !item.isEmpty()) {
                    try {
                        String username = authModel.getLogin();
                        Path path = Paths.get(serverPath.getText(), item);
                        FileList files = new FileList();
                        files.setOwner(username);
                        files.setFiles(new ArrayList<>());
                        files.setPath(path.toString());
                        os.writeObject(files);
                        os.flush();
                        log.debug("request files for user:{} from path:{}", username, path);
                    } catch (Exception e) {
                        log.error("e", e);
                    }

                }
            }
        });

        //
        clientBack.setOnMouseClicked(event -> {
            if (!currentClientDir.equals(clientDir)) {
                currentClientDir = currentClientDir.getParent();
                log.debug("currentClientDir:{}", currentClientDir);
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
                    ImageView imageView = new ImageView();

                    @Override
                    public void updateItem(String name, boolean empty) {
                        super.updateItem(name, empty);
                        if (empty) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            File file = currentClientDir.resolve(name).toFile();
                            if (file.isDirectory())
                                imageView.setImage(IMAGE_FOLDER);
                            else if (file.isFile())
                                imageView.setImage(IMAGE_DOC);
                            setText(name);
                            setGraphic(imageView);
                        }
                    }
                };
//                ContextMenu contextMenu = new ContextMenu();
//                MenuItem editItem = new MenuItem();
//                editItem.textProperty().bind(Bindings.format("Edit \"%s\"", listCell.itemProperty()));
//                editItem.setOnAction(event -> {
//                    String item = listCell.getItem();
//                    // code to edit item...
//                });
//                MenuItem deleteItem = new MenuItem();
//                deleteItem.textProperty().bind(Bindings.format("Delete \"%s\"", listCell.itemProperty()));
//                deleteItem.setOnAction(event -> clientView.getItems().remove(listCell.getItem()));
//                contextMenu.getItems().addAll(editItem, deleteItem);
//
//                listCell.textProperty().bind(listCell.itemProperty());
//
//                listCell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
//                    if (isNowEmpty) {
//                        listCell.setContextMenu(null);
//                    } else {
//                        listCell.setContextMenu(contextMenu);
//                    }
//                });
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
        currentClientDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

        progressBar.setProgress(0);
    }

    private List<String> getFiles(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString())
                .sorted((s1, s2) -> {
                    File file1 = currentClientDir.resolve(s1).toFile();
                    File file2 = currentClientDir.resolve(s2).toFile();
                    if (file1.isDirectory() && !file2.isDirectory()) {
                        return -1;
                    } else if (!file1.isDirectory() && file2.isDirectory()) {
                        return 1; //return String.CASE_INSENSITIVE_ORDER.compare(file1.getName(), file2.getName());
                    } else {
                        return 0;
                    }
                })
                .collect(Collectors.toList());
    }

    private void updateListView() {

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
                        log.debug("user:{} is success auth", authModel.getLogin());
                        showInformStringMessage(authModel.getResponse());
                        String username = authModel.getLogin();
                        FileList fileList = new FileList();
                        fileList.setOwner(username);
                        fileList.setFiles(new ArrayList<>());
                        os.writeObject(fileList);
                        os.flush();
                        log.debug("request files for user:{}", username);
                    } else {
                        showErrorStringMessage(authModel.getResponse());
                        log.error("{}", authModel.getResponse());
                    }
                    continue;
                }
                if (obj.getClass() == FileList.class) {
                    FileList fileList = (FileList) obj;
                    List<FileInfo> files = fileList.getFileInfoList();
                    String path = fileList.getPath();
                    log.debug("files on server:{} from path:{}", files, path);

                    currentServerFiles = files;
                    updateListView();

                    Platform.runLater(() -> {
                        //serverView.getItems().clear();
                        //serverView.getItems().addAll(files);
                        //serverPath.setText(path);

//                        if (path.getNameCount() == 3) {
//                            serverPath.setText("");
//                        } else {
//                            serverPath.setText(path.subpath(3, path.getNameCount()).toString());
//                        }
                    });
                    continue;
                }
                if (obj.getClass() == FileModel.class) {
                    FileModel model = (FileModel) obj;
                    String filePath = model.getFilePath();
                    Path file = clientDir.resolve(filePath);

                    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                        log.debug("try download file:{}", filePath);
                        log.debug("open stream for receive file:{}", filePath);

                        while (true) {
                            fos.write(model.getData(), 0, model.getBatchLength());
                            log.debug("received file:{}; batch:{}/{}", filePath, model.getCurrentBatch(), model.getCountBatch());
                            if (model.getCurrentBatch() == model.getCountBatch()) {
                                break;
                            }
                            model = (FileModel) is.readObject();
                        }
                        log.debug("close stream for receive file:{}", filePath);
                        log.debug("download file:{} is success", filePath);
                    } catch (Exception e) {
                        log.error("e", e);
                    }
                    Platform.runLater(() -> {
                        try {
                            clientView.getItems().clear();
                            clientView.getItems().addAll(getFiles(file.getParent()));

                            if (file.getParent().getNameCount() == 2) {
                                currentClientDir = clientDir;
                                clientPath.setText("");
                            } else {
                                currentClientDir = file.getParent();
                                clientPath.setText(file.subpath(2, file.getNameCount() - 1).toString());
                            }

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
        if (!authModel.isAuth()) {
            showErrorStringMessage("You dont auth!");
            return;
        }
        Path myFile = currentClientDir.resolve(fileName);
        log.debug("try send file:{}", myFile.toFile());
        if (!myFile.toFile().exists()) {
            showErrorStringMessage(String.format("File %s is not exist!", fileName));
            log.error("file:{} not exists", fileName);
            return;
        }
        if (myFile.toFile().isDirectory()) {
            showErrorStringMessage(String.format("Cannot send directory %s!", fileName));
            log.error("file:{} is directory", fileName);
            return;
        }

        long fileLength = myFile.toFile().length();
        long batchCount = (fileLength + BUFFER_SIZE - 1) / BUFFER_SIZE;
        if (batchCount == 0) {
            showErrorStringMessage(String.format("File %s is empty!", fileName));
            log.error("file:{} is empty", fileName);
            return;
        }
        long i = 1;
        log.debug("upload file:{}; length:{}; batch count:{} ", fileName, fileLength, batchCount);

        try (FileInputStream fis = new FileInputStream(myFile.toFile())) {
            while (fis.available() > 0) {
                int read = fis.read(buf);

                FileModel model = new FileModel();
                model.setOwner(authModel.getLogin());
                model.setFilePath(myFile.subpath(2, myFile.getNameCount()).toString());
                model.setData(buf);
                model.setCountBatch(batchCount);
                model.setCurrentBatch(i++);
                model.setBatchLength(read);

                os.writeObject(model);
                log.debug("send file:{}, batch :{}/{}", fileName, model.getCurrentBatch(), model.getCountBatch());

                double current = (double) i / batchCount;
                progressBar.setProgress(current);
            }
        }
        os.flush();
        log.debug("upload file:{} is successful", fileName);
    }

    public void download(ActionEvent actionEvent) throws IOException {
        download(serverView.getSelectionModel().getSelectedItem());
    }

    public void download(String fileName) throws IOException {
        if (!authModel.isAuth()) {
            showErrorStringMessage("You dont auth!");
            return;
        }
        FileRequest requestModel = new FileRequest();
        requestModel.setOwner(authModel.getLogin());
        requestModel.setFilePath(serverPath.getText());
        requestModel.setFileName(fileName);
        os.writeObject(requestModel);
        os.flush();
        log.debug("request file:{}\\\\{}", serverPath.getText(), fileName);
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
                                    clientView.getItems().addAll(getFiles(currentClientDir));
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

                log.debug("send request on auth for user:{}", authModel.getLogin());
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
