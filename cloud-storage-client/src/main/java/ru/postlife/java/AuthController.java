package ru.postlife.java;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import ru.postlife.java.model.AuthModel;


@Slf4j
public class AuthController {

    @FXML
    private TextField loginField;
    @FXML
    private PasswordField passwordField;

    private AuthModel authModel;

    public void setAuthModel(AuthModel authModel) {
        this.authModel = authModel;
    }

    public void btnTryAuth(ActionEvent actionEvent) {
        authModel.setLogin(loginField.getText().trim());
        authModel.setPassword(passwordField.getText().trim());
        closeStage(actionEvent);
    }

    private void closeStage(ActionEvent event) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.close();
    }
}