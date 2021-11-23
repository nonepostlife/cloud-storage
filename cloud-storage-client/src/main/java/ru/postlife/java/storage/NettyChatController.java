package ru.postlife.java.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
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

    private static final int BUFFER_SIZE = 1024;
    private static final byte ICON_SIZE = 24;

    private final Image FOLDER = new Image(getClass().getResource("icons/folder.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FOLDER_CLOUD = new Image(getClass().getResource("icons/folder_cloud.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FOLDER_SYNC = new Image(getClass().getResource("icons/folder_sync.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FILE = new Image(getClass().getResource("icons/file.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FILE_CLOUD = new Image(getClass().getResource("icons/file_cloud.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FILE_SYNC = new Image(getClass().getResource("icons/file_sync.png").toString(), ICON_SIZE, ICON_SIZE, false, false);

    private final Image BACK = new Image(getClass().getResource("icons/back.png").toString(), 16, 16, false, false);
    private final Image DOWNLOAD = new Image(getClass().getResource("icons/btn_download.png").toString(), 36, 36, false, false);
    private final Image UPLOAD = new Image(getClass().getResource("icons/btn_upload.png").toString(), 36, 36, false, false);

    public ProgressBar progressBar;
    public ListView<String> clientView;
    public TextField input;
    public TextField clientPath;

    public Button uploadBtn;
    public Button downloadBtn;
    public Button backBtn;

    private Socket socket;
    private ObjectEncoderOutputStream os;
    private ObjectDecoderInputStream is;

    private byte[] buf;
    private Path clientDir;
    private Path currentClientDir;

    private Map<String, Boolean> currentServerFilesMap;
    private List<String> currentServerFilenames;
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

        backBtn.setGraphic(new ImageView(BACK));
        downloadBtn.setGraphic(new ImageView(DOWNLOAD));
        uploadBtn.setGraphic(new ImageView(UPLOAD));

        // переход в папку на уровень выше
        backBtn.setOnMouseClicked(event -> {
            if (!currentClientDir.equals(clientDir)) {
                currentClientDir = currentClientDir.getParent();
                log.debug("currentClientDir:{}", currentClientDir);
                try {
                    FileList fileList = new FileList();
                    fileList.setOwner(authModel.getLogin());
                    fileList.setFilenames(new ArrayList<>());
                    if (currentClientDir.getNameCount() > 2) {
                        fileList.setPath(currentClientDir.subpath(2, currentClientDir.getNameCount()).toString());
                    }
                    os.writeObject(fileList);
                    os.flush();
                    log.debug("request files for user:{}", authModel.getLogin());
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
                                FileList fileList = new FileList();
                                fileList.setOwner(authModel.getLogin());
                                fileList.setFilenames(new ArrayList<>());
                                fileList.setPath(currentClientDir.subpath(2, currentClientDir.getNameCount()).toString());
                                os.writeObject(fileList);
                                os.flush();
                                log.debug("request files for user:{}", authModel.getLogin());
                            } catch (IOException e) {
                                log.error("e", e);
                            }
                            clientPath.setText(currentClientDir.subpath(2, currentClientDir.getNameCount()).toString());
                        }
                    }
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
                            // на клиенте
                            if (file.exists()) {
                                if (!currentServerFilesMap.containsKey(name)) {
                                    if (file.isDirectory())
                                        imageView.setImage(FOLDER);
                                    else if (file.isFile())
                                        imageView.setImage(FILE);
                                } else { // + есть на сервере
                                    if (file.isDirectory())
                                        imageView.setImage(FOLDER_SYNC);
                                    else if (file.isFile())
                                        imageView.setImage(FILE_SYNC);
                                }
                            } else { // только на сервере
                                if (currentServerFilesMap.get(name))
                                    imageView.setImage(FOLDER_CLOUD);
                                else
                                    imageView.setImage(FILE_CLOUD);
                            }
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

        WatchService watchService = FileSystems.getDefault().newWatchService();
        runAsync(watchService);
        currentClientDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

        progressBar.setProgress(0);
    }

    private List<String> getFiles(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private void updateListView() {
        List<String> clientFiles = getFiles(currentClientDir);
        Set<String> uniqueFiles = new TreeSet<>();
        uniqueFiles.addAll(clientFiles);
        uniqueFiles.addAll(currentServerFilenames);

        List<String> allFiles = new ArrayList<>(uniqueFiles);
        allFiles.sort((s1, s2) -> {
            File file1 = currentClientDir.resolve(s1).toFile();
            File file2 = currentClientDir.resolve(s2).toFile();
            if (file1.exists() && file2.exists()) {
                if (file1.isDirectory() && !file2.isDirectory()) {
                    return -1;
                } else if (!file1.isDirectory() && file2.isDirectory()) {
                    return 1;
                } else {
                    return 0;
                }
            } else if (file1.exists() && !file2.exists()) {
                if (file1.isDirectory() && !isServerFileDirectory(s2)) {
                    return -1;
                } else if (!file1.isDirectory() && isServerFileDirectory(s2)) {
                    return 1;
                } else {
                    return 0;
                }
            } else if (!file1.exists() && file2.exists()) {
                if (file2.isDirectory() && !isServerFileDirectory(s1)) {
                    return 1;
                } else if (!file2.isDirectory() && isServerFileDirectory(s1)) {
                    return -1;
                } else {
                    return 0;
                }
            } else {
                if (isServerFileDirectory(s1) && !isServerFileDirectory(s2)) {
                    return -1;
                } else if (!isServerFileDirectory(s1) && isServerFileDirectory(s2)) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        clientView.getItems().clear();
        clientView.getItems().addAll(allFiles);
    }

    private boolean isServerFileDirectory(String filename) {
        return currentServerFilesMap.get(filename);
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
                        fileList.setFilenames(new ArrayList<>());
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
                    String path = fileList.getPath();
                    currentServerFilenames = fileList.getFilenames();
                    currentServerFilesMap = fileList.getFilesInfoMap();
                    log.debug("files on server:{} from path:{}", currentServerFilenames, path);

                    Platform.runLater(this::updateListView);
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
        download(clientView.getSelectionModel().getSelectedItem());
    }

    public void download(String fileName) throws IOException {
        if (!authModel.isAuth()) {
            showErrorStringMessage("You dont auth!");
            return;
        }
        FileRequest requestModel = new FileRequest();
        requestModel.setOwner(authModel.getLogin());
        requestModel.setFilePath(clientPath.getText());
        requestModel.setFileName(fileName);
        os.writeObject(requestModel);
        os.flush();
        log.debug("request file:{}\\\\{}", clientPath.getText(), fileName);
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
