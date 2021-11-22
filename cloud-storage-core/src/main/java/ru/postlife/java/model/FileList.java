package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FileList implements Serializable {
    private String owner;
    private String path;
    private List<String> files;
    private List<FileInfo> fileInfoList;

    public FileList() {
        this.path = "";
    }
}
