package com.example.ai01.data.entity;

import jakarta.persistence.*;
@Entity
@Table(name = "markdown_file_state")
public class MarkdownFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    public MarkdownFile() {
    }
    public MarkdownFile(String contentHash, String content) {
        this.contentHash = contentHash;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}