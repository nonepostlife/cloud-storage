package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FileListModel implements Serializable {
    private List<String> files;

    public FileListModel(List<String> files) {
        this.files = files;
    }
}
