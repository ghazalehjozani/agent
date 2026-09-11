package com.example.ai01.exception;

public class FileNotFoundException extends Exception {
    private String fileHash;
    public FileNotFoundException(String message) {
        super(message);
    }

}