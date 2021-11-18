package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileRequestModel implements Serializable {
    private String fileName;
    private String filePath;
    private String owner;

    public FileRequestModel() {}
}
