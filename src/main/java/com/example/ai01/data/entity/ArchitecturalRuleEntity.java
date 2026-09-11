package com.example.ai01.data.entity;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.RuleType;
import com.example.ai01.agent.model.ruleextraction.RuleLocalMetadata;
import com.example.ai01.agent.model.ruleextraction.RuleSemanticMetadata;
import jakarta.persistence.*;
import org.assertj.core.util.Strings;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.sql.SQLType;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(
        name = "architecture_rule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_architecture_rule_version_code",
                columnNames = {"file_name", "rule_code"}
        )
)
public class ArchitecturalRuleEntity {
    @Id
    private UUID id;
    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 64)
    private RuleType ruleType;
    @Column(name = "strategy_key")
    private String strategyKey;
    @ManyToOne
    @JoinColumn(name = "file_name", referencedColumnName = "file_name")
    private MarkdownFileEntity file;
    @Column(name = "semantic_rules", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private RuleSemanticMetadata ruleSemanticMetadata;
    @Column(
            name = "enabled",
            nullable = false,
            columnDefinition = "boolean default true"
    )
    private boolean enabled = true;

    protected ArchitecturalRuleEntity() {
    }

    public ArchitecturalRuleEntity(ArchitectureRule rule) {
        this.id = UUID.randomUUID();
        this.ruleCode = rule.id();
        this.description = rule.description();
        this.ruleType = rule.ruleType();
        this.strategyKey = Optional
                .ofNullable(rule.localMetadata())
                .map((localMetaData) -> localMetaData.strategyKey())
                .orElse("");
        this.ruleSemanticMetadata = rule.semanticMetadata();
    }

    public ArchitecturalRuleEntity(String ruleCode,
                                   String description,
                                   RuleType ruleType,
                                   RuleSemanticMetadata ruleSemanticMetadata,
                                   RuleLocalMetadata ruleLocalMetadata,
                                   MarkdownFileEntity file) {
        this.ruleCode = ruleCode;
        this.description = description;
        this.ruleType = ruleType;
        this.strategyKey = ruleLocalMetadata.strategyKey();
        this.ruleSemanticMetadata = ruleSemanticMetadata;
        this.file = file;
    }

    public ArchitectureRule toDomain() {
        return new ArchitectureRule(ruleCode,
                description,
                ruleType,
                new RuleLocalMetadata(strategyKey),
                ruleSemanticMetadata);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public String getStrategyKey() {
        return strategyKey;
    }

    public void setStrategyKey(String strategyKey) {
        this.strategyKey = strategyKey;
    }

    public RuleSemanticMetadata getRuleSemanticMetadata() {
        return ruleSemanticMetadata;
    }

    public void setRuleSemanticMetadata(RuleSemanticMetadata ruleSemanticMetadata) {
        this.ruleSemanticMetadata = ruleSemanticMetadata;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }


    public MarkdownFileEntity getFile() {
        return file;
    }

    public void setFile(MarkdownFileEntity file) {
        this.file = file;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}