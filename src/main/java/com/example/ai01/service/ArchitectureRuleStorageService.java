package com.example.ai01.service;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.data.entity.ArchitecturalRuleEntity;
import com.example.ai01.data.entity.MarkdownFileEntity;
import com.example.ai01.data.entity.MarkdownFileSnapshotEntity;
import com.example.ai01.data.repository.ArchitectureRuleEntityRepository;
import com.example.ai01.data.repository.MarkDownFileRepository;
import com.example.ai01.data.repository.MarkdownFileSnapShotRepository;
import com.example.ai01.monitoring.TraceOperation;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ArchitectureRuleStorageService {

    private final MarkDownFileRepository markDownFileRepository;
    private final ArchitectureRuleEntityRepository architectureRuleEntityRepository;
    private final MarkdownFileSnapShotRepository markdownFileSnapShotRepository;
    private final StructuralAgentExecutionService structuralAgentExecutionService;
    private final MarkdownSplittingService markdownSplittingService;

    public ArchitectureRuleStorageService(
            MarkDownFileRepository markDownFileRepository,
            ArchitectureRuleEntityRepository architectureRuleEntityRepository,
            MarkdownFileSnapShotRepository markdownFileSnapShotRepository,
            StructuralAgentExecutionService structuralAgentExecutionService,
            MarkdownSplittingService markdownSplittingService) {

        this.markDownFileRepository = markDownFileRepository;
        this.architectureRuleEntityRepository =
                architectureRuleEntityRepository;
        this.markdownFileSnapShotRepository =
                markdownFileSnapShotRepository;
        this.structuralAgentExecutionService =
                structuralAgentExecutionService;
        this.markdownSplittingService =
                markdownSplittingService;
    }

    @Transactional
    @TraceOperation(
            spanName = "rules.storage.resolve",
            newSpan = true,
            spanKind = "RETRIEVER"
    )
    public List<ArchitectureRule> getRules(
            String fileNumber,
            String fileName,
            String hash,
            String content) {

        Span span = Span.current();

        span.updateName(
                "rules.storage.resolve [" + fileName + "]"
        );

        span.setAttribute(
                "openinference.span.kind",
                "RETRIEVER"
        );

        span.setAttribute(
                "rule.file.name",
                fileName
        );

        span.setAttribute(
                "rule.file.hash",
                hash
        );

        span.setAttribute(
                "rule.file.content_length",
                content == null ? 0L : content.length()
        );

        var fileOptional =
                markDownFileRepository.findByFileName(fileName);

        if (fileOptional.isEmpty()) {
            List<ArchitectureRule> rules =
                    insertFile(
                            fileNumber,
                            fileName,
                            hash,
                            content
                    );

            span.setAttribute(
                    "rule.storage.resolution",
                    "INSERTED"
            );

            span.setAttribute(
                    "rule.count",
                    (long) rules.size()
            );

            return rules;
        }

        MarkdownFileEntity file = fileOptional.get();

        /*
         * اگر فایل تغییری نکرده باشد،
         * فقط Ruleهای فعال از دیتابیس خوانده می‌شوند.
         */
        if (file.getContentHash().equals(hash)) {

            List<ArchitectureRule> rules =
                    architectureRuleEntityRepository
                            .findAllByFile_FileNameAndEnabledTrue(
                                    fileName
                            )
                            .stream()
                            .map(ArchitecturalRuleEntity::toDomain)
                            .toList();

            span.setAttribute(
                    "rule.storage.resolution",
                    "CACHE_HIT"
            );

            span.setAttribute(
                    "rule.count",
                    (long) rules.size()
            );

            return rules;
        }

        /*
         * اگر فایل تغییر کرده باشد، وضعیت enabled قبلی
         * هنگام استخراج مجدد حفظ می‌شود.
         */
        List<ArchitectureRule> rules =
                updateFile(
                        fileNumber,
                        file,
                        hash,
                        content
                );

        span.setAttribute(
                "rule.storage.resolution",
                "UPDATED"
        );

        span.setAttribute(
                "rule.count",
                (long) rules.size()
        );

        return rules;
    }

    private List<ArchitectureRule> updateFile(
            String fileNumber,
            MarkdownFileEntity file,
            String newHash,
            String newContent) {

        /*
         * وضعیت فعال یا غیرفعال Ruleهای موجود،
         * پیش از حذف آن‌ها نگهداری می‌شود.
         */
        Map<String, Boolean> enabledStateByRuleCode =
                architectureRuleEntityRepository
                        .findAllByFile_FileName(
                                file.getFileName()
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                ArchitecturalRuleEntity::getRuleCode,
                                ArchitecturalRuleEntity::isEnabled
                        ));

        /*
         * نسخه قبلی فایل Snapshot می‌شود.
         */
        MarkdownFileSnapshotEntity snapshot =
                new MarkdownFileSnapshotEntity(
                        file.getVersion(),
                        file.getContentHash(),
                        file.getContent(),
                        file
                );

        markdownFileSnapShotRepository.save(snapshot);

        /*
         * Ruleهای نسخه قبلی حذف می‌شوند.
         */
        architectureRuleEntityRepository
                .deleteAllByFile_FileName(
                        file.getFileName()
                );

        /*
         * DELETE باید قبل از INSERTهای جدید
         * واقعاً روی دیتابیس اجرا شود.
         */
        architectureRuleEntityRepository.flush();

        /*
         * Ruleهای جدید از محتوای به‌روز‌شده استخراج می‌شوند.
         */
        List<ArchitectureRule> extractedRules =
                extractUniqueRules(
                        fileNumber,
                        newContent
                );

        file.setContentHash(newHash);
        file.setContent(newContent);
        file.setVersion(file.getVersion() + 1);

        markDownFileRepository.save(file);

        /*
         * Ruleهای جدید با وضعیت enabled قبلی ذخیره می‌شوند.
         */
        saveRules(
                file,
                extractedRules,
                enabledStateByRuleCode
        );

        /*
         * Ruleهای disabled در همین اجرای جاری
         * نیز از خروجی کنار گذاشته می‌شوند.
         */
        return filterEnabledRules(
                extractedRules,
                enabledStateByRuleCode
        );
    }

    private List<ArchitectureRule> insertFile(
            String fileNumber,
            String fileName,
            String hash,
            String content) {

        MarkdownFileEntity file =
                new MarkdownFileEntity(
                        fileName,
                        hash,
                        content,
                        1L
                );

        List<ArchitectureRule> rules =
                extractUniqueRules(
                        fileNumber,
                        content
                );

        file = markDownFileRepository.save(file);

        /*
         * Ruleهای کاملاً جدید به‌صورت پیش‌فرض فعال هستند.
         */
        saveRules(
                file,
                rules,
                Map.of()
        );

        return rules;
    }

    private List<ArchitectureRule> extractUniqueRules(
            String fileNumber,
            String content) {

        List<String> chunks =
                markdownSplittingService.split(content);

        List<ArchitectureRule> rawRules = chunks
                .stream()
                .flatMap(chunk ->
                        extractWithSplitRetry(chunk)
                                .stream()
                )
                .toList();

        List<ArchitectureRule> extractedRules =
                java.util.stream.IntStream
                        .range(0, rawRules.size())
                        .mapToObj(index -> {
                            ArchitectureRule rule =
                                    rawRules.get(index);

                            String numberedRuleId =
                                    rule.id() == null
                                            ? null
                                            : "%03d%s".formatted(
                                            index + 1,
                                            rule.id()
                                    );

                            ArchitectureRule numberedRule =
                                    new ArchitectureRule(
                                            numberedRuleId,
                                            rule.description(),
                                            rule.ruleType(),
                                            rule.localMetadata(),
                                            rule.semanticMetadata()
                                    );

                            return prefixRuleId(
                                    fileNumber,
                                    numberedRule
                            );
                        })
                        .toList();

        /*
         * جلوگیری از duplicate شدن ruleCode.
         */
        return new ArrayList<>(
                extractedRules.stream()
                        .collect(Collectors.toMap(
                                ArchitectureRule::id,
                                Function.identity(),
                                (first, duplicate) -> first,
                                LinkedHashMap::new
                        ))
                        .values()
        );
    }

    private List<ArchitectureRule> extractWithSplitRetry(
            String chunk) {

        try {
            return structuralAgentExecutionService
                    .extract(chunk)
                    .rules();

        } catch (
                dev.langchain4j.service.output.OutputParsingException
                        exception) {

            boolean truncatedOutput = false;

            for (Throwable cause = exception;
                 cause != null;
                 cause = cause.getCause()) {

                String message = cause.getMessage();

                if (message != null
                        && message.contains(
                        "Unexpected end-of-input"
                )) {

                    truncatedOutput = true;
                    break;
                }
            }

            if (!truncatedOutput) {
                throw exception;
            }

            int midpoint = chunk.length() / 2;
            int minimumBoundary = chunk.length() / 4;
            int maximumBoundary =
                    chunk.length() - minimumBoundary;

            int splitAt = -1;
            int nearestDistance = Integer.MAX_VALUE;

            var sentenceBoundary =
                    java.util.regex.Pattern
                            .compile(
                                    "(?<=[.!?\u061F])\\s+|\\R\\s*\\R"
                            )
                            .matcher(chunk);

            while (sentenceBoundary.find()) {
                int candidate =
                        sentenceBoundary.end();

                if (candidate < minimumBoundary
                        || candidate > maximumBoundary) {
                    continue;
                }

                int distance =
                        Math.abs(candidate - midpoint);

                if (distance < nearestDistance) {
                    splitAt = candidate;
                    nearestDistance = distance;
                }
            }

            if (splitAt < 0) {
                throw exception;
            }

            String firstHalf =
                    chunk.substring(0, splitAt).trim();

            String secondHalf =
                    chunk.substring(splitAt).trim();

            if (firstHalf.isEmpty()
                    || secondHalf.isEmpty()) {
                throw exception;
            }

            List<ArchitectureRule> rules =
                    new ArrayList<>();

            rules.addAll(
                    extractWithSplitRetry(firstHalf)
            );

            rules.addAll(
                    extractWithSplitRetry(secondHalf)
            );

            return rules;
        }
    }

    ArchitectureRule prefixRuleId(
            String fileNumber,
            ArchitectureRule rule) {

        if (fileNumber == null
                || !fileNumber.matches("\\d{2}")) {

            throw new IllegalArgumentException(
                    "fileNumber must contain exactly two digits"
            );
        }

        String normalizedRuleId =
                rule.id() == null
                        ? ""
                        : rule.id()
                        .replaceAll(
                                "[^A-Za-z0-9]",
                                ""
                        )
                        .toUpperCase(Locale.ROOT);

        if (normalizedRuleId.isBlank()) {
            throw new IllegalArgumentException(
                    "Extracted rule id must contain letters or digits"
            );
        }

        String prefixedRuleId =
                fileNumber + normalizedRuleId;

        if (prefixedRuleId.length() > 64) {
            throw new IllegalArgumentException(
                    "Prefixed rule id exceeds the database limit: "
                            + prefixedRuleId
            );
        }

        return new ArchitectureRule(
                prefixedRuleId,
                rule.description(),
                rule.ruleType(),
                rule.localMetadata(),
                rule.semanticMetadata()
        );
    }

    private List<ArchitectureRule> filterEnabledRules(
            List<ArchitectureRule> rules,
            Map<String, Boolean> enabledStateByRuleCode) {

        return rules.stream()
                .filter(rule ->
                        enabledStateByRuleCode.getOrDefault(
                                rule.id(),
                                true
                        )
                )
                .toList();
    }

    private void saveRules(
            MarkdownFileEntity file,
            List<ArchitectureRule> rules,
            Map<String, Boolean> enabledStateByRuleCode) {

        List<ArchitecturalRuleEntity> entities =
                rules.stream()
                        .map(rule -> {
                            ArchitecturalRuleEntity entity =
                                    new ArchitecturalRuleEntity(
                                            rule
                                    );

                            entity.setFile(file);

                            /*
                             * وضعیت قبلی Rule حفظ می‌شود.
                             * Rule جدید به‌صورت پیش‌فرض فعال است.
                             */
                            entity.setEnabled(
                                    enabledStateByRuleCode
                                            .getOrDefault(
                                                    rule.id(),
                                                    true
                                            )
                            );

                            return entity;
                        })
                        .toList();

        architectureRuleEntityRepository.saveAll(entities);
    }
}