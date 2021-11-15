package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class AuthModel implements Serializable {
    private String login;
    private String password;

    private String firstname;
    private String lastname;

    private String response;
    private boolean isAuth;
}
