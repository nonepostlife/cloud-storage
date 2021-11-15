package ru.postlife.java.storage;

import ru.postlife.java.model.AuthModel;

public interface AuthService {
    void start();

    void stop();

    AuthModel checkUserByLoginPass(AuthModel authModel);

    String registerNewUser(String login, String password);
}
