package com.example.ai01.data.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.Id;

import java.util.UUID;

@Entity
@Table(
        name = "architecture_rule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_architecture_rule_version_code",
                columnNames = {"version_id", "rule_code"}
        )
)
public class ArchitectureRuleEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private RuleExtractionVersionEntity version;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 64)
    private RuleCategory category;
    protected ArchitectureRuleEntity() {
    }
    public ArchitectureRuleEntity(RuleExtractionVersionEntity version, ArchitectureRule rule) {
        this.id = UUID.randomUUID();
        this.version = version;
        this.ruleCode = rule.id();
        this.description = rule.description();
        this.category = rule.category();
    }
    public ArchitectureRule toDomain() {
        return new ArchitectureRule(ruleCode, description, category);
    }
    public UUID getId() {
        return id;
    }
    public RuleExtractionVersionEntity getVersion() {
        return version;
    }
    public String getRuleCode() {
        return ruleCode;
    }
    public String getDescription() {
        return description;
    }
    public RuleCategory getCategory() {
        return category;
    }

}
