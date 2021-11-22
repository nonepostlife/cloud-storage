package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileInfo implements Serializable {
    private String fileName;
    private boolean isDirectory;
}
