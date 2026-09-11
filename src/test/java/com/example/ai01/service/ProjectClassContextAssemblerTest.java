package com.example.ai01.service;

import com.example.ai01.agent.model.AccessModifier;
import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.ClassType;
import com.example.ai01.agent.model.Constructor;
import com.example.ai01.agent.model.Field;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.Method;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.Parameter;
import com.example.ai01.model.ClassRuleCheckBatch;
import com.example.ai01.model.RuleCheckAssignment;
import com.example.ai01.model.RuleCheckBatch;
import com.example.ai01.service.batch.BatchingProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectClassContextAssemblerTest {

    @Test
    void replacesRetrievalSegmentsWithOneCompleteCompactClassTree() {
        BatchingProperties properties = new BatchingProperties();
        properties.setMaxFiles(5);
        properties.setMaxInputTokens(12_000);

        ProjectClassContextAssembler assembler =
                new ProjectClassContextAssembler(properties);

        String filePath = "/workspace/ComplianceController.java";

        JavaFile javaFile = new JavaFile(
                "ComplianceController.java",
                filePath,
                "com.example.controller",
                List.of("org.springframework.web.bind.annotation.RestController"),
                ClassType.CLASS,
                List.of(new Annotation("RestController", Map.of())),
                null,
                List.of(),
                List.of(new Field(
                        List.of(),
                        AccessModifier.PRIVATE,
                        "ComplianceService",
                        "service"
                )),
                List.of(new Constructor(
                        AccessModifier.PUBLIC,
                        List.of(),
                        List.of(new Parameter("ComplianceService", "service"))
                )),
                List.of(new Method(
                        "analyze",
                        AccessModifier.PUBLIC,
                        List.of(new Annotation(
                                "PostMapping",
                                Map.of("value", "\"/analyze\"")
                        )),
                        "ComplianceResponse",
                        List.of(new Parameter("AnalysisRequest", "request")),
                        false
                )),
                900,
                30
        );

        PackageNode projectTree = new PackageNode(
                "",
                "",
                List.of(new PackageNode(
                        "controller",
                        "com.example.controller",
                        List.of(),
                        List.of(javaFile)
                )),
                List.of()
        );

        Metadata metadata = new Metadata();
        metadata.put("segmentId", "segment-1");
        metadata.put("segmentType", "METHOD");
        metadata.put("filePath", filePath);

        RuleCheckBatch retrievalBatch = new RuleCheckBatch(
                "FILE:" + filePath,
                List.of(),
                List.of(TextSegment.from(
                        "THIS RETRIEVAL SEGMENT MUST NOT REACH THE AGENT",
                        metadata
                )),
                List.of(new RuleCheckAssignment(
                        "API-001",
                        List.of(filePath),
                        List.of("segment-1")
                ))
        );

        List<ClassRuleCheckBatch> result = assembler.assemble(
                projectTree,
                List.of(retrievalBatch)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rules()).isEmpty();
        assertThat(result.get(0).classContexts()).hasSize(1);

        String tree = result.get(0).classContexts().get(0).compactTree();

        assertThat(tree)
                .startsWith("path: /workspace/ComplianceController.java")
                .contains("package: com.example.controller")
                .contains("type: CLASS")
                .contains("@RestController")
                .contains("PRIVATE ComplianceService service")
                .contains("PUBLIC constructor(ComplianceService service)")
                .contains("PUBLIC ComplianceResponse analyze(AnalysisRequest request)")
                .contains("@PostMapping(value=\"/analyze\")")
                .doesNotContain("CLASS ComplianceController")
                .doesNotContain("THIS RETRIEVAL SEGMENT MUST NOT REACH THE AGENT")
                .doesNotContain("segment-1");
    }

    @Test
    void loadJavaFileFindsAClassByNormalizedPath() {
        ProjectClassContextAssembler assembler =
                new ProjectClassContextAssembler(new BatchingProperties());

        JavaFile javaFile = new JavaFile(
                "Sample.java",
                "C:\\workspace\\Sample.java",
                "com.example",
                List.of(),
                ClassType.CLASS,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                10,
                1
        );

        PackageNode root = new PackageNode(
                "",
                "",
                List.of(),
                List.of(javaFile)
        );

        assertThat(assembler.loadJavaFile(
                root,
                "C:/workspace/Sample.java"
        )).contains(javaFile);
    }
    @Test
    void resolvesPackageSegmentToClassesInThatPackageAndItsChildren() {
        BatchingProperties properties = new BatchingProperties();
        properties.setMaxFiles(5);
        properties.setMaxInputTokens(12_000);

        ProjectClassContextAssembler assembler =
                new ProjectClassContextAssembler(properties);

        JavaFile javaFile = new JavaFile(
                "BillingService.java",
                "/workspace/com/example/billing/BillingService.java",
                "com.example.billing",
                List.of(),
                ClassType.CLASS,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                20,
                1
        );

        PackageNode root = new PackageNode(
                "",
                "",
                List.of(new PackageNode(
                        "billing",
                        "com.example.billing",
                        List.of(),
                        List.of(javaFile)
                )),
                List.of()
        );

        Metadata metadata = new Metadata();
        metadata.put("segmentId", "package-segment-1");
        metadata.put("segmentType", "PACKAGE");
        metadata.put("packageName", "com.example");

        RuleCheckBatch retrievalBatch = new RuleCheckBatch(
                "ADAPTIVE:45[SEGMENT:package-segment-1#part-1]",
                List.of(),
                List.of(TextSegment.from("PACKAGE com.example", metadata)),
                List.of(new RuleCheckAssignment(
                        "PKG-001",
                        List.of(),
                        List.of("package-segment-1")
                ))
        );

        List<ClassRuleCheckBatch> result = assembler.assemble(
                root,
                List.of(retrievalBatch)
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).classContexts())
                .extracting(context -> context.filePath())
                .containsExactly(javaFile.path());
    }
}

