package com.example.ai01.data.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "markdown_file_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_markdown_snapshot_version_path",
                columnNames = {"version", "file_name"})
)
public class MarkdownFileSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "version")
    private Long version;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(
            name = "file_content",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String content;
    @ManyToOne
    @JoinColumn(name = "file_name", referencedColumnName = "file_name")
    private MarkdownFileEntity file;

    public MarkdownFileSnapshotEntity() {
    }

    public MarkdownFileSnapshotEntity(Long version, String contentHash, String content, MarkdownFileEntity file) {
        this.version = version;
        this.contentHash = contentHash;
        this.content = content;
        this.file = file;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public MarkdownFileEntity getFile() {
        return file;
    }

    public void setFile(MarkdownFileEntity file) {
        this.file = file;
    }
}