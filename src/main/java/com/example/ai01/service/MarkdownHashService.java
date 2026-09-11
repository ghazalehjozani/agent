package com.example.ai01.service;

import com.example.ai01.monitoring.TraceOperation;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class MarkdownHashService {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 16 * 1024;

    @TraceOperation(serviceName = "hash-service", spanName = "markdown.hash", newSpan = true , spanKind = "TOOL")
    public String sha256(Path markdownFile) throws IOException {
        Path file = validateMarkdownFile(markdownFile);

        //
        Span span = Span.current();
        String fileName = file.getFileName().toString();

        span.updateName("markdown.hash [" + fileName + "]");
        span.setAttribute("openinference.span.kind", "TOOL");
        span.setAttribute("file.name", fileName);
        span.setAttribute(
                "file.path",
                file.toAbsolutePath().normalize().toString()
        );
        span.setAttribute("file.size.bytes", Files.size(file));
        span.setAttribute("hash.algorithm", HASH_ALGORITHM);
        //

        MessageDigest digest = newDigest();

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        var hash = HexFormat.of().formatHex(digest.digest());
        span.setAttribute("hash.value", hash);
        return hash;
    }

    public String sha256(byte[] content) {
        MessageDigest digest = newDigest();
        return HexFormat.of().formatHex(digest.digest(content));
    }

    /**
     * Returns hashes in deterministic path order. LinkedHashMap is intentional:
     * order is preserved and can later be used to build a stable prompt/fingerprint.
     */
    public LinkedHashMap<Path, String> hashAll(Collection<Path> markdownFiles) throws IOException {
        if (markdownFiles == null || markdownFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one Markdown file is required");
        }

        LinkedHashMap<Path, String> hashes = new LinkedHashMap<>();
        for (Path path : markdownFiles.stream()
                .map(this::normalize)
                .distinct()
                .sorted(Comparator.comparing(this::portablePath))
                .toList()) {
            hashes.put(path, sha256(path));
        }
        return hashes;
    }

    /**
     * Hash of the complete input set. The file-level hashes remain available separately,
     * while this value identifies the exact aggregate input used for one extraction version.
     */
    public String fingerprint(Map<Path, String> orderedHashes) {
        MessageDigest digest = newDigest();
        orderedHashes.forEach((path, hash) -> {
            digest.update(portablePath(path).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(hash.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) '\n');
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    public byte[] readVerified(Path markdownFile, String expectedHash) throws IOException {
        Path file = validateMarkdownFile(markdownFile);
        byte[] bytes = Files.readAllBytes(file);
        String actualHash = sha256(bytes);
        if (!actualHash.equals(expectedHash)) {
            throw new IllegalStateException("Markdown changed while extraction was running: " + file);
        }
        return bytes;
    }

    private Path validateMarkdownFile(Path markdownFile) throws IOException {
        Path file = normalize(markdownFile);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Markdown file does not exist or is not a regular file: " + file);
        }
        String name = file.getFileName().toString().toLowerCase();
        if (!name.endsWith(".md")) {
            throw new IllegalArgumentException("Only .md files are accepted: " + file);
        }
        return file;
    }

    private Path normalize(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Markdown path cannot be null");
        }
        return path.toAbsolutePath().normalize();
    }

    private String portablePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

}
