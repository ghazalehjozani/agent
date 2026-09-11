package com.example.ai01.service;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.monitoring.TraceOperation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

@Service
public class RuleExtractionService {
    private final Executor ruleExtractionExecutor;
    private MarkdownHashService markdownHashService;
    private ArchitectureRuleStorageService architectureRuleStorageService;

    public RuleExtractionService(@Qualifier("ruleExtractionExecutor") Executor ruleExtractionExecutor,
                                 MarkdownHashService markdownHashService,
                                 ArchitectureRuleStorageService architectureRuleStorageService) {
        this.ruleExtractionExecutor = ruleExtractionExecutor;
        this.markdownHashService = markdownHashService;
        this.architectureRuleStorageService = architectureRuleStorageService;
    }

    @TraceOperation(serviceName = "rule-extraction", spanName = "rules.extract", newSpan = true)
    public CompletableFuture<List<ArchitectureRule>> extractRules(
            List<String> rulesPath) {

        List<String> orderedPaths = rulesPath.stream()
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize().toString())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        if (orderedPaths.size() > 99) {
            throw new IllegalArgumentException(
                    "At most 99 architecture-rule files are supported"
            );
        }

        var futures = IntStream.range(0, orderedPaths.size())
                .mapToObj(index -> {
                    String fileNumber = "%02d".formatted(index + 1);
                    String path = orderedPaths.get(index);

                    return CompletableFuture.supplyAsync(
                            () -> loadRules(path, fileNumber),
                            ruleExtractionExecutor
                    );
                })
                .toList();

        return CompletableFuture
                .allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored ->
                        futures.stream()
                                .flatMap(future ->
                                        future.join().stream()
                                )
                                .toList()
                );
    }

    private List<ArchitectureRule> loadRules(
            String rulesPath,
            String fileNumber) {

        Path path = Path.of(rulesPath);

        try {

            String fileName =
                    path.getFileName().toString();

            String hash =
                    markdownHashService.sha256(path);

            String content =
                    Files.readString(
                            path,
                            StandardCharsets.UTF_8
                    );

            return architectureRuleStorageService.getRules(
                    fileNumber,
                    fileName,
                    hash,
                    content
            );

        } catch (IOException e) {

            throw new UncheckedIOException(
                    "Could not read markdown rules file: " + rulesPath,
                    e
            );
        }
    }

}
