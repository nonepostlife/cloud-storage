package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class FileList implements Serializable {
    private String owner;
    private String path;
    private List<String> filenames;
    private Map<String, Boolean> filesInfoMap;

    public FileList() {
        this.path = "";
    }
}
