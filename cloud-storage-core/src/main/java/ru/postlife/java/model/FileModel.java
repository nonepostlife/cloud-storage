package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileModel implements Serializable {
    private String owner;

    private String fileName;
    private byte[] data;

    private long countBatch;
    private long currentBatch;
    private int batchLength;
}
