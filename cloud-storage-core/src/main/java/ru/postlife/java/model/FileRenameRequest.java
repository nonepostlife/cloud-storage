package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileRenameRequest implements Serializable {
    private String owner;
    private String filePath;
    private String oldName;
    private String newName;

    public FileRenameRequest() {
        filePath = "";
    }
}
