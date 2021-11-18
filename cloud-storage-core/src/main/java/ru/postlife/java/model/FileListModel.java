package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FileListModel implements Serializable {
    private String owner;
    private String path;
    private List<String> files;

    public FileListModel() {
        this.path = "";
    }
}
