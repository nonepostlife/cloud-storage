package ru.postlife.java.storage;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class NettyApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent parent = FXMLLoader.load(getClass().getResource("form.fxml"));
        primaryStage.setScene(new Scene(parent));
        primaryStage.setTitle("CloudStorage");
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(600);
        primaryStage.show();
    }
}
