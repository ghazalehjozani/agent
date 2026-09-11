package com.example.ai01.service;

import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.monitoring.TraceOperation;
import com.example.ai01.tools.ProjectStructureExtractor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class JavaStructureService {

    private final ProjectStructureExtractor projectStructureExtractor;
    private final ExecutorService auditExecutor;

    public JavaStructureService(
            ProjectStructureExtractor projectStructureExtractor,
            @Qualifier("auditExecutor")
            ExecutorService auditExecutor) {

        this.projectStructureExtractor =
                projectStructureExtractor;

        this.auditExecutor =
                auditExecutor;
    }

    /*
     * newSpan=true باعث می‌شود project.structure.extract
     * به‌صورت یک گره مستقل زیر architecture.audit در Phoenix
     * نمایش داده شود.
     */
    @TraceOperation(
            serviceName = "java-structure",
            spanName = "project.structure.extract",
            newSpan = true,
            spanKind = "TOOL"
    )
    public CompletableFuture<PackageNode> extract(
            String projectRoot) {

        return CompletableFuture.supplyAsync(
                () -> projectStructureExtractor
                        .readJavaFiles(projectRoot),
                auditExecutor
        );
    }
}