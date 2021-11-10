package ru.postlife.java.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class FileModel implements Serializable {
    private String fileName;
    private long fileLength;
    private byte[] data;

    private long countBatch;
    private long currentBatch;
    private int batchLength;
}
