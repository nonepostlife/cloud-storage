package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileDeleteRequest implements Serializable {
    private String owner;
    private String filePath;
    private String fileName;

    public FileDeleteRequest() {}
}
