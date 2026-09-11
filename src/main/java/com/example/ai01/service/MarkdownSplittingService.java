package com.example.ai01.service;

import com.example.ai01.monitoring.TraceOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class MarkdownSplittingService {
    private final int maxChunkChars;
    public MarkdownSplittingService(@Value("${app.rule-extraction.max-chunk-chars:12000}") int maxChunkChars) {
        this.maxChunkChars = maxChunkChars;
    }
    @TraceOperation(serviceName = "markdown-splitting-service", spanName = "markdown.split", newSpan = true)
    public List<String> split(String content) {

        if (content == null || content.isBlank()) {
            return List.of();
        }

        String normalizedContent = content.trim();

        /*
         * فایل کوچک است.
         * نیازی به split نداریم.
         */
        if (normalizedContent.length() <= maxChunkChars) {
            return List.of(normalizedContent);
        }

        String documentTitle =
                extractDocumentTitle(normalizedContent);

        /*
         * ابتدا بر اساس H2
         */
        List<String> h2Sections =
                splitByHeading(
                        normalizedContent,
                        2
                );

        List<String> result =
                new ArrayList<>();

        for (String section : h2Sections) {

            /*
             * Section به اندازه کافی کوچک است.
             */
            if (section.length() <= maxChunkChars) {

                result.add(
                        addDocumentContext(
                                documentTitle,
                                section
                        )
                );

                continue;
            }

            /*
             * H2 بزرگ است.
             * آن را بر اساس H3 تقسیم می‌کنیم.
             */
            List<String> h3Sections =
                    splitByHeading(
                            section,
                            3
                    );

            /*
             * اگر H3 پیدا نشد یعنی split مؤثری اتفاق نیفتاده.
             */
            if (h3Sections.size() == 1) {

                result.addAll(
                        splitLargeSection(
                                documentTitle,
                                section
                        )
                );

                continue;
            }

            for (String subSection : h3Sections) {

                if (subSection.length() <= maxChunkChars) {

                    result.add(
                            addDocumentContext(
                                    documentTitle,
                                    subSection
                            )
                    );

                } else {

                    result.addAll(
                            splitLargeSection(
                                    documentTitle,
                                    subSection
                            )
                    );
                }
            }
        }

        return result;
    }

    /**
     * Split Markdown based on heading level.
     * <p>
     * level = 2 -> ##
     * level = 3 -> ###
     */
    private List<String> splitByHeading(
            String content,
            int level) {

        String hashes =
                "#".repeat(level);

        String regex =
                "(?m)(?=^"
                        + hashes
                        + "\\s+)";

        return Arrays.stream(
                        content.split(regex)
                )
                .map(String::trim)
                .filter(section ->
                        !section.isBlank()
                )
                .toList();
    }

    /**
     * اگر حتی H3 هم خیلی بزرگ بود،
     * بر اساس paragraph تقسیم می‌کنیم.
     */
    private List<String> splitLargeSection(
            String documentTitle,
            String section) {

        List<String> chunks =
                new ArrayList<>();

        String[] paragraphs =
                section.split("\\R\\s*\\R");

        StringBuilder current =
                new StringBuilder();

        for (String paragraph : paragraphs) {

            if (paragraph.isBlank()) {
                continue;
            }

            /*
             * اگر اضافه شدن paragraph باعث شود
             * chunk از limit بزرگ‌تر شود،
             * chunk فعلی بسته می‌شود.
             */
            if (!current.isEmpty()
                    && current.length()
                    + paragraph.length()
                    + 2
                    > maxChunkChars) {

                chunks.add(
                        addDocumentContext(
                                documentTitle,
                                current.toString()
                        )
                );

                current.setLength(0);
            }

            /*
             * خود paragraph از limit بزرگ‌تر است.
             */
            if (paragraph.length() > maxChunkChars) {

                if (!current.isEmpty()) {

                    chunks.add(
                            addDocumentContext(
                                    documentTitle,
                                    current.toString()
                            )
                    );

                    current.setLength(0);
                }

                chunks.addAll(
                        splitBySize(
                                documentTitle,
                                paragraph
                        )
                );

                continue;
            }

            if (!current.isEmpty()) {
                current.append("\n\n");
            }

            current.append(paragraph);
        }

        if (!current.isEmpty()) {

            chunks.add(
                    addDocumentContext(
                            documentTitle,
                            current.toString()
                    )
            );
        }

        return chunks;
    }

    /**
     * آخرین fallback.
     * <p>
     * اگر یک paragraph بسیار بزرگ بود،
     * بر اساس اندازه split می‌شود.
     */
    private List<String> splitBySize(
            String documentTitle,
            String text) {

        List<String> result =
                new ArrayList<>();

        int start = 0;

        while (start < text.length()) {

            int end =
                    Math.min(
                            start + maxChunkChars,
                            text.length()
                    );

            String chunk =
                    text.substring(
                            start,
                            end
                    );

            result.add(
                    addDocumentContext(
                            documentTitle,
                            chunk
                    )
            );

            start = end;
        }

        return result;
    }

    /**
     * H1 سند را پیدا می‌کند.
     * <p>
     * مثال:
     * # 3.1.2 Request/Response schema
     */
    private String extractDocumentTitle(
            String content) {

        return content.lines()
                .map(String::trim)
                .filter(line ->
                        line.startsWith("# ")
                )
                .findFirst()
                .map(line ->
                        line.substring(2).trim()
                )
                .orElse("Architecture Document");
    }

    /**
     * context اصلی Document را به هر chunk اضافه می‌کند
     * تا LLM بداند این section مربوط به چه سندی است.
     */
    private String addDocumentContext(
            String documentTitle,
            String section) {

        return """
                DOCUMENT:
                %s

                CONTENT:
                %s
                """.formatted(
                documentTitle,
                section.trim()
        );
    }
}