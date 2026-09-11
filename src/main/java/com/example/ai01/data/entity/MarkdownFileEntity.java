package com.example.ai01.data.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "markdown_files",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_markdown_file_name",
                columnNames = {"file_name"}
        ))
public class MarkdownFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "file_name", nullable = false, updatable = false, length = 100)
    private String fileName;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(
            name = "file_content",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String content;
    @Column(name = "version", nullable = false)
    private Long version;
    @OneToMany(mappedBy = "file", fetch = FetchType.LAZY)
    private List<MarkdownFileSnapshotEntity> markdownFileSnapshotEntity;

    @OneToMany(mappedBy = "file", fetch = FetchType.LAZY)
    private List<ArchitecturalRuleEntity> architecturalRuleEntity;

    public MarkdownFileEntity() {
    }

    public MarkdownFileEntity(String fileName, String contentHash, String content, Long version) {
        this.fileName = fileName;
        this.contentHash = contentHash;
        this.content = content;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<MarkdownFileSnapshotEntity> getMarkdownFileSnapshotEntity() {
        return markdownFileSnapshotEntity;
    }

    public void setMarkdownFileSnapshotEntity(List<MarkdownFileSnapshotEntity> markdownFileSnapshotEntity) {
        this.markdownFileSnapshotEntity = markdownFileSnapshotEntity;
    }

    public List<ArchitecturalRuleEntity> getArchitecturalRuleEntity() {
        return architecturalRuleEntity;
    }

    public void setArchitecturalRuleEntity(List<ArchitecturalRuleEntity> architecturalRuleEntity) {
        this.architecturalRuleEntity = architecturalRuleEntity;
    }
}