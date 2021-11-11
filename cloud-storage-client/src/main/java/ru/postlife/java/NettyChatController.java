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
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.util.Callback;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.FileListModel;
import ru.postlife.java.model.FileModel;
import ru.postlife.java.model.FileRequestModel;

import static java.nio.file.StandardWatchEventKinds.*;

@Slf4j
public class NettyChatController implements Initializable {

    private static final int BUFFER_SIZE = 1024;
    public ProgressBar progressBar;

    private byte[] buf;
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
                    content.putString(listCell.getItem());
                    db.setContent(content);
                    System.out.println(db.getString());
                    event.consume();
                });
                return listCell;
            }
        });

        serverView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> param) {
                ListCell<String> listCell = new ListCell<String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null) {
                            setText(item);
                        }
                    }
                };
                listCell.setOnDragOver((DragEvent event) ->
                {
                    Dragboard db = event.getDragboard();
                    if (db.hasString()) {
                        event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    }
                    event.consume();
                });
                listCell.setOnDragDropped((DragEvent event) ->
                {
                    System.out.println("treeCell.setOnDragDropped");
                    Dragboard db = event.getDragboard();
                    boolean success = false;
                    if (db.hasString()) {
                        System.out.println("Dropped: " + db.getString());
                        try {
                            sendFile(db.getString());
                        } catch (IOException e) {
                            log.error("e", e);
                        }
                        success = true;
                    }
                    event.setDropCompleted(success);
                    event.consume();
                });
                return listCell;
            }
        });

        progressBar.setProgress(0);

        Socket socket = new Socket("localhost", 8189);
        os = new ObjectEncoderOutputStream(socket.getOutputStream());
        is = new ObjectDecoderInputStream(socket.getInputStream());
        Thread thread = new Thread(this::read);
        thread.setDaemon(true);
        thread.start();

        WatchService watchService = FileSystems.getDefault().newWatchService();
        runAsync(watchService);
        clientDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

        FileListModel files = new FileListModel(new ArrayList<>());
        os.writeObject(files);
        os.flush();
    }

    private List<String> getFiles(Path path) throws IOException {
        return Files.list(path).map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private void read() {
        while (true) {
            Object obj = is.readObject();
            log.debug("receive object {}", obj);

            if (obj.getClass() == FileListModel.class) {
                FileListModel fileListModel = (FileListModel) obj;
                List<String> files = fileListModel.getFiles();
                Platform.runLater(() -> {
                    serverView.getItems().clear();
                    serverView.getItems().addAll(files);
                });
                log.debug("files on server: {}", files);
                continue;
            }
            if (obj.getClass() == FileModel.class) {
                FileModel model = (FileModel) obj;
                String fileName = model.getFileName();
                Path file = clientDir.resolve(fileName);

                try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                    log.debug("try download file: {}", fileName);
                    log.debug("open stream for receive file  \"{}\"", fileName);

                    while (true) {
                        fos.write(model.getData(), 0, model.getBatchLength());
                        log.debug("received: {} batch {}/{}", fileName, model.getCurrentBatch(), model.getCountBatch());
                        model = (FileModel) is.readObject();
                        if (model.getCurrentBatch() == model.getCountBatch()) {
                            fos.write(model.getData(), 0, model.getBatchLength());
                            log.debug("received: {} batch {}/{}", fileName, model.getCurrentBatch(), model.getCountBatch());
                            break;
                        }
                    }

                    log.debug("close stream for receive file \"{}\"", fileName);
                    log.debug("download file {} is success", fileName);
                } catch (Exception e) {
                    log.error("e", e);
                }
                Platform.runLater(() -> {
                    try {
                        clientView.getItems().clear();
                        clientView.getItems().addAll(getFiles(clientDir));
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
                continue;
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
        File myFile = new File(clientDir.resolve(fileName).toString());
        log.debug("try send file - {}", myFile.getAbsolutePath());
        if (!myFile.exists()) {
            log.error("{} not exists", fileName);
            return;
        }
        if (myFile.isDirectory()) {
            log.error("{} is not file", fileName);
            return;
        }

        long fileLength = myFile.length();
        long batchCount = (fileLength + BUFFER_SIZE - 1) / BUFFER_SIZE;
        long i = 1;
        log.debug("upload file: {} ; batch count {} ", fileName, batchCount);

        try (FileInputStream fis = new FileInputStream(myFile)) {
            while (fis.available() > 0) {
                int read = fis.read(buf);

                FileModel model = new FileModel();
                model.setFileName(fileName);
                model.setFileLength(fileLength);
                model.setData(buf);
                model.setCountBatch(batchCount);
                model.setCurrentBatch(i++);
                model.setBatchLength(read);

                os.writeObject(model);
                log.debug("send {} batch {}/{}", fileName, model.getCurrentBatch(), model.getCountBatch());

                double current = (double) i / batchCount;
                progressBar.setProgress(current);
                System.out.println(current);
            }
        }
        os.flush();
    }

    public void upload(ActionEvent actionEvent) throws IOException {
        upload(serverView.getSelectionModel().getSelectedItem());
    }

    public void upload(String fileName) throws IOException {
        FileRequestModel requestModel = new FileRequestModel(fileName);
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
}
