package ru.postlife.java.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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

import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;


import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.*;

import static java.nio.file.StandardWatchEventKinds.*;

@Slf4j
public class NettyController implements Initializable {

    private static final int BUFFER_SIZE = 1024;
    private static final byte ICON_SIZE = 24;

    private final Image FOLDER = new Image(getClass().getResource("icons/folder.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FOLDER_CLOUD = new Image(getClass().getResource("icons/folder_cloud.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FOLDER_SYNC = new Image(getClass().getResource("icons/folder_sync.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FILE = new Image(getClass().getResource("icons/file.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FILE_CLOUD = new Image(getClass().getResource("icons/file_cloud.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image FILE_SYNC = new Image(getClass().getResource("icons/file_sync.png").toString(), ICON_SIZE, ICON_SIZE, false, false);

    private final Image BACK = new Image(getClass().getResource("icons/back.png").toString(), 16, 16, false, false);

    private final Image DOWNLOAD = new Image(getClass().getResource("icons/btn_download.png").toString());
    private final Image UPLOAD = new Image(getClass().getResource("icons/btn_upload.png").toString());
    private final Image DELETE_CLIENT = new Image(getClass().getResource("icons/delete_client.png").toString());
    private final Image DELETE_CLOUD = new Image(getClass().getResource("icons/delete_cloud.png").toString());

    private final Image DOWNLOAD_SMALL = new Image(getClass().getResource("icons/btn_download.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image UPLOAD_SMALL = new Image(getClass().getResource("icons/btn_upload.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image DELETE_CLIENT_SMALL = new Image(getClass().getResource("icons/delete_client.png").toString(), ICON_SIZE, ICON_SIZE, false, false);
    private final Image DELETE_CLOUD_SMALL = new Image(getClass().getResource("icons/delete_cloud.png").toString(), ICON_SIZE, ICON_SIZE, false, false);

    public ProgressBar progressBar;
    public ListView<String> clientView;
    public TextField input;
    public TextField clientPath;

    public Button uploadBtn;
    public Button downloadBtn;
    public Button backBtn;
    public Button deleteOnClientBtn;
    public Button deleteOnServerBtn;
    public Label info;

    private Socket socket;
    private ObjectEncoderOutputStream os;
    private ObjectDecoderInputStream is;

    private WatchService watchService;

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
        currentServerFilesMap = new HashMap<>();
        currentServerFilenames = new ArrayList<>();
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
        deleteOnClientBtn.setGraphic(new ImageView(DELETE_CLIENT));
        deleteOnServerBtn.setGraphic(new ImageView(DELETE_CLOUD));

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
                    if (newPath.toFile().isDirectory() || currentServerFilesMap.get(item)) {
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
        });

        clientView.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<String>() {
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

            ContextMenu contextMenu = new ContextMenu();
            // upload
            MenuItem uploadItem = new MenuItem();
            uploadItem.textProperty().bind(Bindings.format("Upload on server \"%s\"", cell.itemProperty()));
            uploadItem.setGraphic(new ImageView(UPLOAD_SMALL));
            uploadItem.setOnAction(event -> {
                try {
                    sendFile(cell.getItem());
                } catch (Exception e) {
                    log.error("e", e);
                }
            });
            // download
            MenuItem downloadItem = new MenuItem();
            downloadItem.textProperty().bind(Bindings.format("Download from server \"%s\"", cell.itemProperty()));
            downloadItem.setGraphic(new ImageView(DOWNLOAD_SMALL));
            downloadItem.setOnAction(event -> {
                try {
                    download(cell.getItem());
                } catch (Exception e) {
                    log.error("e", e);
                }
            });
            // delete on client
            MenuItem deleteOnClientItem = new MenuItem();
            deleteOnClientItem.textProperty().bind(Bindings.format("Delete \"%s\"", cell.itemProperty()));
            deleteOnClientItem.setGraphic(new ImageView(DELETE_CLIENT_SMALL));
            deleteOnClientItem.setOnAction(event -> {
                try {
                    deleteOnClient(cell.getItem());
                } catch (IOException e) {
                    log.error("e", e);
                }
            });
            // delete on client
            MenuItem deleteOnCloudItem = new MenuItem();
            deleteOnCloudItem.textProperty().bind(Bindings.format("Delete on cloud \"%s\"", cell.itemProperty()));
            deleteOnCloudItem.setGraphic(new ImageView(DELETE_CLOUD_SMALL));
            deleteOnCloudItem.setOnAction(event -> {
                try {
                    deleteOnServer(cell.getItem());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            contextMenu.getItems().addAll(uploadItem, downloadItem, deleteOnClientItem, deleteOnCloudItem);

            cell.textProperty().bind(cell.itemProperty());
            cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
                if (isNowEmpty) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(contextMenu);
                }
            });
            cell.textProperty().unbind();
            return cell;
        });
        watchService = FileSystems.getDefault().newWatchService();
        runAsync(watchService);
        registerRecursive(clientDir);

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
                        return 1;
                    } else {
                        return 0;
                    }
                })
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private void updateListView() {
        List<String> clientFiles = new ArrayList<>();
        if (currentClientDir.toFile().exists()) {
            clientFiles = getFiles(currentClientDir);
        }
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
                if (obj.getClass() == AuthModel.class) {
                    authModel = (AuthModel) obj;
                    log.debug("receive authModel:{}", authModel);
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
                    log.debug("receive fileList:{}", fileList);
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
                    if (!file.getParent().toFile().exists()) {
                        Files.createDirectories(file.getParent());
                        registerRecursive(file.getParent());
                    }
                    try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                        log.debug("try download file:{}", filePath);
                        log.debug("open stream for receive file:{}", filePath);
                        Platform.runLater(() -> progressBar.setProgress(0));
                        while (true) {
                            fos.write(model.getData(), 0, model.getBatchLength());
                            log.debug("received file:{}; batch:{}/{}", filePath, model.getCurrentBatch(), model.getCountBatch());
                            double currentBatch = (double) model.getCurrentBatch() / model.getCountBatch();
                            Platform.runLater(() -> progressBar.setProgress(currentBatch));
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
                    Platform.runLater(this::updateListView);
                    continue;
                }
                if (obj.getClass() == String.class) {
                    String msg = (String) obj;
                    log.debug("receive msg:{}", msg);
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

    public void sendFile(ActionEvent actionEvent) throws IOException {
        if (socket != null && !socket.isClosed()) {
            sendFile(clientView.getSelectionModel().getSelectedItem());
        } else {
            showErrorStringMessage("No authorization on the server");
            log.warn("No authorization on the server");
        }
    }

    public void sendFile(String filename) {
        // TODO: 27.11.2021 отправка папки
        if (!authModel.isAuth()) {
            showErrorStringMessage("You dont auth!");
            return;
        }
        Thread thread = new Thread(() -> {
            Path myFile = currentClientDir.resolve(filename);
            log.debug("try send file:{}", myFile.toFile());
            if (!myFile.toFile().exists()) {
                showErrorStringMessage(String.format("File %s is not exist!", filename));
                log.error("file:{} not exists", filename);
                return;
            }
            if (myFile.toFile().isDirectory()) {
                showErrorStringMessage(String.format("Cannot send directory %s!", filename));
                log.error("file:{} is directory", filename);
                return;
            }

            long fileLength = myFile.toFile().length();
            long batchCount = (fileLength + BUFFER_SIZE - 1) / BUFFER_SIZE;
            if (batchCount == 0) {
                showErrorStringMessage(String.format("File %s is empty!", filename));
                log.error("file:{} is empty", filename);
                return;
            }
            long i = 1;
            log.debug("upload file:{}; length:{}; batch count:{} ", filename, fileLength, batchCount);

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
                    log.debug("send file:{}, batch :{}/{}", filename, model.getCurrentBatch(), model.getCountBatch());

                    double current = (double) i / batchCount;
                    progressBar.setProgress(current);
                }
            } catch (IOException e) {
                log.error("e", e);
            }
            try {
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.debug("upload file:{} is successful", filename);
            Platform.runLater(() -> info.setText(String.format("Upload file %s is successful", filename)));
        });
        thread.start();
    }

    public void download(ActionEvent actionEvent) throws IOException {
        if (socket != null && !socket.isClosed()) {
            download(clientView.getSelectionModel().getSelectedItem());
        } else {
            showErrorStringMessage("No authorization on the server");
            log.warn("No authorization on the server");
        }
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

    public void deleteOnClient(ActionEvent actionEvent) throws IOException {
        if (socket != null && !socket.isClosed()) {
            deleteOnClient(clientView.getSelectionModel().getSelectedItem());
        } else {
            showErrorStringMessage("No authorization on the server");
            log.warn("No authorization on the server");
        }
    }

    public void deleteOnClient(String filename) throws IOException {
        Path filePath = currentClientDir.resolve(filename);
        Files.deleteIfExists(filePath);
        log.info("File:{} was deleted on client", filePath);
        info.setText(String.format("File %s was deleted", filename));
    }

    public void deleteOnServer(ActionEvent actionEvent) throws IOException {
        if (socket != null && !socket.isClosed()) {
            deleteOnServer(clientView.getSelectionModel().getSelectedItem());
        } else {
            showErrorStringMessage("No authorization on the server");
            log.warn("No authorization on the server");
        }
    }

    public void deleteOnServer(String fileName) throws IOException {
        if (!authModel.isAuth()) {
            showErrorStringMessage("You dont auth!");
            return;
        }
        FileDeleteRequest deleteRequest = new FileDeleteRequest();
        deleteRequest.setOwner(authModel.getLogin());
        deleteRequest.setFilePath(clientPath.getText());
        deleteRequest.setFileName(fileName);
        os.writeObject(deleteRequest);
        os.flush();
        log.debug("file delete request: file:{}\\\\{}", clientPath.getText(), fileName);
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
                            Platform.runLater(this::updateListView);
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

    private void registerRecursive(final Path root) throws IOException {
        // register all subfolders
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
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

    public void disconnect(ActionEvent actionEvent) {
        closeConnectionAndResources();
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
}
