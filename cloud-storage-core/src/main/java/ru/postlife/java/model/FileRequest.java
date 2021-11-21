package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileRequest implements Serializable {
    private String owner;
    private String filePath;
    private String fileName;

    public FileRequest() {}
}
