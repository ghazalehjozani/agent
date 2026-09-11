package com.example.ai01.service;

import com.example.ai01.agent.model.*;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import com.example.ai01.agent.model.ruleextraction.ProjectElementType;
import com.example.ai01.agent.model.ruleextraction.RuleScope;
import com.example.ai01.agent.model.vector.EmbeddedRule;
import com.example.ai01.model.RuleProjectMatch;
import com.example.ai01.monitoring.TraceOperation;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.springframework.util.StringUtils.truncate;

@Service
public class ProjectEmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> projectEmbeddingStore;
    private final Executor embeddingExecutor;

    public ProjectEmbeddingService(
            EmbeddingModel embeddingModel,
            @Qualifier("projectEmbeddingStore")
            EmbeddingStore<TextSegment> projectEmbeddingStore,
            @Qualifier("embeddingExecutor")
            Executor embeddingExecutor
    ) {

        this.embeddingModel = embeddingModel;
        this.projectEmbeddingStore = projectEmbeddingStore;
        this.embeddingExecutor = embeddingExecutor;
    }


    // =========================================================
    // Search
    // =========================================================

    @TraceOperation(serviceName = "project-embedding", spanName = "embedding.project.search", newSpan = true, spanKind = "RETRIEVER")
    public List<RuleProjectMatch> search(
            String projectId,
            ArchitectureRule rule,
            EmbeddedRule embeddedRule,
            double minScore,
            int maxResults) {

        //
        Span span = Span.current();
        span.updateName("embedding.project.search [" + rule.id() + "]");
        span.setAttribute("openinference.span.kind", "RETRIEVER");
        span.setAttribute("project.id", projectId);
        span.setAttribute("rule.id", rule.id());
        span.setAttribute("rule.type", rule.ruleType().name());
        span.setAttribute("search.min_score", minScore);
        span.setAttribute("search.max_results", (long) maxResults);
        span.setAttribute("rule.description", truncate(rule.description(), 1000));
        span.setAttribute("rule.semantic_group", rule.semanticMetadata().semanticGroup());
        //

        //
        if (rule.semanticMetadata() != null) {
            span.setAttribute("rule.scope", rule.semanticMetadata().scope().name());
            span.setAttribute("rule.concern", rule.semanticMetadata().concern());
        }
        //

        String segmentType =
                resolveSegmentType(rule);

        //
        span.setAttribute("search.segment_type", segmentType == null ? "ALL" : segmentType);
        //

        Filter filter =
                metadataKey("projectId")
                        .isEqualTo(projectId);


        if (segmentType != null) {

            filter = filter.and(
                    metadataKey("segmentType")
                            .isEqualTo(segmentType)
            );
        }


        EmbeddingSearchRequest request =
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(
                                embeddedRule.embedding()
                        )
                        .filter(filter)
                        .minScore(minScore)
                        .maxResults(maxResults)
                        .build();


        EmbeddingSearchResult<TextSegment> result =
                projectEmbeddingStore.search(request);


        //

        span.setAttribute("search.match_count", (long) result.matches().size());
        if (!result.matches().isEmpty()) {
            span.setAttribute("search.best_score", result.matches().get(0).score());
        }

        List<String> matchedFiles =
                result.matches()
                        .stream()
                        .map(match ->
                                match.embedded()
                                        .metadata()
                                        .getString("filePath")
                        )
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        span.setAttribute(AttributeKey.stringArrayKey("search.matched_files"), matchedFiles);

        //


        //
        List<Double> scores =
                result.matches()
                        .stream()
                        .map(match -> match.score())
                        .toList();

        span.setAttribute(AttributeKey.doubleArrayKey("search.match_scores"),scores);
        //

        return result.matches()
                .stream()
                .map(match -> {

                    TextSegment segment =
                            match.embedded();

                    Metadata metadata =
                            segment.metadata();


                    return new RuleProjectMatch(
                            rule,
                            match.score(),
                            metadata.getString("segmentId"),
                            metadata.getString("segmentType"),
                            metadata.getString("filePath"),
                            segment
                    );
                })
                .toList();
    }

    private String resolveSegmentType(
            ArchitectureRule rule
    ) {

        if (rule == null
                || rule.semanticMetadata() == null) {

            return null;
        }


        RuleScope scope =
                rule.semanticMetadata()
                        .scope();


        if (scope == null) {
            return null;
        }


        return switch (scope) {

            case PACKAGE -> "PACKAGE";

            case CLASS -> "CLASS";

            case DEPENDENCY -> "DEPENDENCY";

            case METHOD -> "METHOD";


            /*
             * فعلاً Field داخل ClassSegment قرار دارد.
             */
            case FIELD -> "CLASS";


            /*
             * Endpoint و API فعلاً
             * داخل MethodSegment بررسی می‌شوند.
             */
            case ENDPOINT, API -> "METHOD";


            /*
             * در سطح Project همه Segmentها
             * می‌توانند Candidate باشند.
             */
            case PROJECT -> null;


            /*
             * برای Scopeهایی که هنوز Segment تخصصی
             * نداریم، Search روی همه Segmentهای پروژه
             * انجام می‌شود.
             */
            default -> null;
        };
    }


    // =========================================================
    // Async Store
    // =========================================================


    @TraceOperation(serviceName = "project-embedding", spanName = "embedding.project.store", newSpan = true, spanKind = "EMBEDDING")
    public CompletableFuture<Void> storeAsync(
            String projectId,
            PackageNode projectRoot
    ) {

        return CompletableFuture.runAsync(
                () -> store(
                        projectId,
                        projectRoot
                ),
                embeddingExecutor
        );
    }


    // =========================================================
    // Store Project Embeddings
    // =========================================================

    public void store(
            String projectId,
            PackageNode projectRoot
    ) {

        Objects.requireNonNull(
                projectId,
                "projectId must not be null"
        );


        /*
         * Embeddingهای قبلی همین Project
         * پاک می‌شوند.
         */
        projectEmbeddingStore.removeAll(
                metadataKey("projectId")
                        .isEqualTo(projectId)
        );


        if (projectRoot == null) {
            return;
        }


        /*
         * Index کلاس‌های Project برای resolve کردن
         * dependency target type.
         */
        Map<String, JavaFile> classIndex =
                buildClassIndex(projectRoot);


        List<TextSegment> segments =
                new ArrayList<>();


        collectSegments(
                projectId,
                projectRoot,
                classIndex,
                segments
        );


        if (segments.isEmpty()) {
            return;
        }


        /*
         * تمام Project Segments به صورت Batch
         * Embed می‌شوند.
         */
        List<Embedding> embeddings =
                embeddingModel
                        .embedAll(segments)
                        .content();


        /*
         * هر Segment باید یک UUID معتبر
         * به عنوان Embedding ID داشته باشد.
         *
         * segmentId هنگام ساخت Segment
         * توسط createSegmentId ساخته شده است.
         */
        List<String> ids =
                segments.stream()
                        .map(segment -> {

                            String segmentId =
                                    segment.metadata()
                                            .getString(
                                                    "segmentId"
                                            );


                            if (segmentId == null
                                    || segmentId.isBlank()) {

                                throw new IllegalStateException(
                                        "Segment does not contain segmentId"
                                );
                            }


                            /*
                             * Fail-fast validation.
                             *
                             * PgVectorEmbeddingStore نیز ID را
                             * به UUID تبدیل می‌کند، بنابراین
                             * قبل از ارسال خودمان اعتبار آن را
                             * بررسی می‌کنیم.
                             */
                            UUID.fromString(
                                    segmentId
                            );


                            return segmentId;
                        })
                        .toList();


        if (ids.size()
                != embeddings.size()
                || ids.size()
                != segments.size()) {

            throw new IllegalStateException(
                    "Embedding batch sizes do not match: "
                            + "ids=" + ids.size()
                            + ", embeddings=" + embeddings.size()
                            + ", segments=" + segments.size()
            );
        }


        projectEmbeddingStore.addAll(
                ids,
                embeddings,
                segments
        );
    }


    // =========================================================
    // Segment Identity
    // =========================================================

    /**
     * یک UUID پایدار برای هر Segment تولید می‌کند.
     * <p>
     * UUID بر اساس:
     * <p>
     * projectId + segmentKey
     * <p>
     * ساخته می‌شود.
     * <p>
     * بنابراین برای یک Project و Segment ثابت،
     * UUID همیشه یکسان خواهد بود.
     */
    private String createSegmentId(
            String projectId,
            String segmentKey
    ) {

        String value =
                projectId
                        + "|"
                        + segmentKey;


        return UUID.nameUUIDFromBytes(
                value.getBytes(
                        StandardCharsets.UTF_8
                )
        ).toString();
    }


    /**
     * Metadataهای مشترک مربوط به Identity
     * تمام Segmentها را اضافه می‌کند.
     * <p>
     * segmentKey:
     * شناسه خوانای application-level.
     * <p>
     * segmentId:
     * UUID مورد استفاده PgVector.
     */
    private void addIdentityMetadata(
            Metadata metadata,
            String projectId,
            String segmentKey
    ) {

        String segmentId =
                createSegmentId(
                        projectId,
                        segmentKey
                );


        metadata.put(
                "projectId",
                projectId
        );

        metadata.put(
                "segmentKey",
                segmentKey
        );

        metadata.put(
                "segmentId",
                segmentId
        );
    }


    // =========================================================
    // Tree traversal
    // =========================================================

    private void collectSegments(
            String projectId,
            PackageNode node,
            Map<String, JavaFile> classIndex,
            List<TextSegment> result
    ) {


        /*
         * Root node تو qualifiedName خالی دارد.
         */
        if (node.qualifiedName() != null
                && !node.qualifiedName().isBlank()) {

            result.add(
                    createPackageSegment(
                            projectId,
                            node
                    )
            );
        }


        for (JavaFile file :
                node.javaFiles()) {


            /*
             * یک Class Segment
             */
            result.add(
                    createClassSegment(
                            projectId,
                            file
                    )
            );


            /*
             * صفر تا چند Dependency Segment
             */
            result.addAll(
                    createDependencySegments(
                            projectId,
                            file,
                            classIndex
                    )
            );


            /*
             * صفر تا چند Method Segment
             */
            result.addAll(
                    createMethodSegments(
                            projectId,
                            file
                    )
            );
        }


        /*
         * Recursive traversal
         */
        for (PackageNode child :
                node.packages()) {

            collectSegments(
                    projectId,
                    child,
                    classIndex,
                    result
            );
        }
    }


    // =========================================================
    // Package Segment
    // =========================================================

    private TextSegment createPackageSegment(
            String projectId,
            PackageNode node
    ) {

        String classes =
                node.javaFiles()
                        .stream()
                        .map(
                                JavaFile::fileName
                        )
                        .sorted()
                        .collect(
                                Collectors.joining(", ")
                        );


        String childPackages =
                node.packages()
                        .stream()
                        .map(
                                PackageNode::qualifiedName
                        )
                        .sorted()
                        .collect(
                                Collectors.joining(", ")
                        );


        String text = """
                Project Element:
                PACKAGE

                Package:
                %s

                Classes:
                %s

                Child Packages:
                %s
                """.formatted(
                safe(
                        node.qualifiedName()
                ),
                classes,
                childPackages
        );


        /*
         * Human-readable stable key.
         */
        String segmentKey =
                "PACKAGE:"
                        + safe(
                        node.qualifiedName()
                );


        Metadata metadata =
                new Metadata();


        metadata.put(
                "documentType",
                "PROJECT_SEGMENT"
        );


        metadata.put(
                "segmentType",
                "PACKAGE"
        );


        addIdentityMetadata(
                metadata,
                projectId,
                segmentKey
        );


        metadata.put(
                "packageName",
                safe(
                        node.qualifiedName()
                )
        );


        return TextSegment.from(
                text,
                metadata
        );
    }


    // =========================================================
    // Class Segment
    // =========================================================

    private TextSegment createClassSegment(
            String projectId,
            JavaFile file
    ) {

        String className =
                removeJavaExtension(
                        file.fileName()
                );


        ProjectElementType elementType =
                detectElementType(
                        file
                );


        String annotations =
                file.annotations()
                        .stream()
                        .map(
                                Annotation::name
                        )
                        .sorted()
                        .collect(
                                Collectors.joining(", ")
                        );


        String fields =
                file.fields()
                        .stream()
                        .map(field ->
                                field.accessModifier()
                                        + " "
                                        + field.type()
                                        + " "
                                        + field.name()
                        )
                        .sorted()
                        .collect(
                                Collectors.joining("\n")
                        );


        String constructors =
                file.constructors()
                        .stream()
                        .map(constructor ->
                                constructor.accessModifier()
                                        + " constructor("
                                        + formatParameters(
                                        constructor.parameters()
                                )
                                        + ")"
                        )
                        .collect(
                                Collectors.joining("\n")
                        );


        String methods =
                file.methods()
                        .stream()
                        .map(method ->
                                method.accessModifier()
                                        + " "
                                        + method.returnType()
                                        + " "
                                        + method.name()
                                        + "("
                                        + formatParameters(
                                        method.parameters()
                                )
                                        + ")"
                        )
                        .collect(
                                Collectors.joining("\n")
                        );


        String text = """
                Project Element:
                CLASS

                Class Name:
                %s

                Architectural Type:
                %s

                Package:
                %s

                Java Class Type:
                %s

                Annotations:
                %s

                Imports:
                %s

                Super Class:
                %s

                Interfaces:
                %s

                Fields:
                %s

                Constructors:
                %s

                Methods:
                %s
                """.formatted(
                className,
                elementType,
                safe(
                        file.packageName()
                ),
                safe(
                        file.classType()
                ),
                annotations,
                String.join(
                        ", ",
                        file.imports()
                ),
                safe(
                        file.superClass()
                ),
                String.join(
                        ", ",
                        file.interfaces()
                ),
                fields,
                constructors,
                methods
        );


        /*
         * Example:
         *
         * CLASS:com.example.ai01.service.UserService
         */
        String segmentKey =
                "CLASS:"
                        + safe(
                        file.packageName()
                )
                        + "."
                        + className;


        Metadata metadata =
                new Metadata();


        metadata.put(
                "documentType",
                "PROJECT_SEGMENT"
        );


        metadata.put(
                "segmentType",
                "CLASS"
        );


        addIdentityMetadata(
                metadata,
                projectId,
                segmentKey
        );


        metadata.put(
                "filePath",
                safe(
                        file.path()
                )
        );


        metadata.put(
                "fileName",
                safe(
                        file.fileName()
                )
        );


        metadata.put(
                "packageName",
                safe(
                        file.packageName()
                )
        );


        metadata.put(
                "className",
                className
        );


        metadata.put(
                "elementType",
                elementType.name()
        );


        return TextSegment.from(
                text,
                metadata
        );
    }


    // =========================================================
    // Dependency Segments
    // =========================================================

    private List<TextSegment> createDependencySegments(
            String projectId,
            JavaFile file,
            Map<String, JavaFile> classIndex
    ) {

        List<TextSegment> result =
                new ArrayList<>();


        Set<String> alreadyAddedTargets =
                new HashSet<>();


        /*
         * Fields یکی از مهم‌ترین نشانه‌های
         * dependency هستند.
         */
        for (Field field :
                file.fields()) {

            String target =
                    simpleType(
                            field.type()
                    );


            if (target.isBlank()) {
                continue;
            }


            result.add(
                    createDependencySegment(
                            projectId,
                            file,
                            target,
                            "DEPENDS_ON",
                            "FIELD",
                            classIndex
                    )
            );


            alreadyAddedTargets.add(
                    target
            );
        }


        /*
         * Constructor parameters نیز dependency هستند.
         *
         * اگر همان dependency قبلاً از field پیدا شده،
         * دوباره Segment تولید نمی‌کنیم.
         */
        for (Constructor constructor :
                file.constructors()) {

            for (Parameter parameter :
                    constructor.parameters()) {

                String target =
                        simpleType(
                                parameter.type()
                        );


                if (target.isBlank()
                        || alreadyAddedTargets.contains(
                        target
                )) {

                    continue;
                }


                result.add(
                        createDependencySegment(
                                projectId,
                                file,
                                target,
                                "DEPENDS_ON",
                                "CONSTRUCTOR_PARAMETER",
                                classIndex
                        )
                );


                alreadyAddedTargets.add(
                        target
                );
            }
        }


        /*
         * Inheritance
         */
        if (file.superClass() != null
                && !file.superClass().isBlank()) {

            result.add(
                    createDependencySegment(
                            projectId,
                            file,
                            simpleType(
                                    file.superClass()
                            ),
                            "EXTENDS",
                            "SUPER_CLASS",
                            classIndex
                    )
            );
        }


        /*
         * Interface implementation
         */
        for (String interfaceName :
                file.interfaces()) {

            result.add(
                    createDependencySegment(
                            projectId,
                            file,
                            simpleType(
                                    interfaceName
                            ),
                            "IMPLEMENTS",
                            "INTERFACE",
                            classIndex
                    )
            );
        }


        return result;
    }


    private TextSegment createDependencySegment(
            String projectId,
            JavaFile sourceFile,
            String targetClass,
            String relation,
            String dependencySource,
            Map<String, JavaFile> classIndex
    ) {

        String sourceClass =
                removeJavaExtension(
                        sourceFile.fileName()
                );


        ProjectElementType sourceType =
                detectElementType(
                        sourceFile
                );


        ProjectElementType targetType =
                resolveTargetType(
                        targetClass,
                        classIndex
                );


        String text = """
                Project Element:
                DEPENDENCY

                Source Class:
                %s

                Source Type:
                %s

                Relation:
                %s

                Target Class:
                %s

                Target Type:
                %s

                Dependency Source:
                %s
                """.formatted(
                sourceClass,
                sourceType,
                relation,
                targetClass,
                targetType,
                dependencySource
        );


        /*
         * Example:
         *
         * DEPENDENCY:
         * com.example.UserService:
         * DEPENDS_ON:
         * UserRepository
         */
        String segmentKey =
                "DEPENDENCY:"
                        + safe(
                        sourceFile.packageName()
                )
                        + "."
                        + sourceClass
                        + ":"
                        + relation
                        + ":"
                        + targetClass;


        Metadata metadata =
                new Metadata();


        metadata.put(
                "documentType",
                "PROJECT_SEGMENT"
        );


        metadata.put(
                "segmentType",
                "DEPENDENCY"
        );


        addIdentityMetadata(
                metadata,
                projectId,
                segmentKey
        );


        metadata.put(
                "filePath",
                safe(
                        sourceFile.path()
                )
        );


        metadata.put(
                "sourceClass",
                sourceClass
        );


        metadata.put(
                "sourceType",
                sourceType.name()
        );


        metadata.put(
                "targetClass",
                targetClass
        );


        metadata.put(
                "targetType",
                targetType.name()
        );


        metadata.put(
                "relation",
                relation
        );


        metadata.put(
                "dependencySource",
                dependencySource
        );


        return TextSegment.from(
                text,
                metadata
        );
    }


    // =========================================================
    // Method Segments
    // =========================================================

    private List<TextSegment> createMethodSegments(
            String projectId,
            JavaFile file
    ) {

        List<TextSegment> result =
                new ArrayList<>();


        String className =
                removeJavaExtension(
                        file.fileName()
                );


        ProjectElementType classType =
                detectElementType(
                        file
                );


        for (Method method :
                file.methods()) {

            String parameters =
                    formatParameters(
                            method.parameters()
                    );


            String annotations =
                    method.annotations()
                            .stream()
                            .map(
                                    Annotation::name
                            )
                            .sorted()
                            .collect(
                                    Collectors.joining(", ")
                            );


            String text = """
                    Project Element:
                    METHOD

                    Owner Class:
                    %s

                    Owner Type:
                    %s

                    Method:
                    %s

                    Access Modifier:
                    %s

                    Return Type:
                    %s

                    Parameters:
                    %s

                    Annotations:
                    %s

                    Static:
                    %s
                    """.formatted(
                    className,
                    classType,
                    method.name(),
                    method.accessModifier(),
                    method.returnType(),
                    parameters,
                    annotations,
                    method.isStatic()
            );


            /*
             * Signature برای overloadهای یک Method
             * ضروری است.
             */
            String signature =
                    method.parameters()
                            .stream()
                            .map(
                                    Parameter::type
                            )
                            .collect(
                                    Collectors.joining(",")
                            );


            /*
             * Example:
             *
             * METHOD:
             * com.example.UserService
             * #findUser(Long)
             */
            String segmentKey =
                    "METHOD:"
                            + safe(
                            file.packageName()
                    )
                            + "."
                            + className
                            + "#"
                            + method.name()
                            + "("
                            + signature
                            + ")";


            Metadata metadata =
                    new Metadata();


            metadata.put(
                    "documentType",
                    "PROJECT_SEGMENT"
            );


            metadata.put(
                    "segmentType",
                    "METHOD"
            );


            addIdentityMetadata(
                    metadata,
                    projectId,
                    segmentKey
            );


            metadata.put(
                    "filePath",
                    safe(
                            file.path()
                    )
            );


            metadata.put(
                    "fileName",
                    safe(
                            file.fileName()
                    )
            );


            metadata.put(
                    "packageName",
                    safe(
                            file.packageName()
                    )
            );


            metadata.put(
                    "className",
                    className
            );


            metadata.put(
                    "elementType",
                    classType.name()
            );


            metadata.put(
                    "methodName",
                    method.name()
            );


            metadata.put(
                    "methodSignature",
                    signature
            );


            result.add(
                    TextSegment.from(
                            text,
                            metadata
                    )
            );
        }


        return result;
    }


    // =========================================================
    // Class index
    // =========================================================

    private Map<String, JavaFile> buildClassIndex(
            PackageNode root
    ) {

        Map<String, JavaFile> result =
                new HashMap<>();


        collectClassIndex(
                root,
                result
        );


        return Map.copyOf(
                result
        );
    }


    private void collectClassIndex(
            PackageNode node,
            Map<String, JavaFile> result
    ) {

        for (JavaFile file :
                node.javaFiles()) {

            String className =
                    removeJavaExtension(
                            file.fileName()
                    );


            /*
             * Simple name
             */
            result.put(
                    className,
                    file
            );


            /*
             * Qualified name
             */
            result.put(
                    safe(
                            file.packageName()
                    )
                            + "."
                            + className,
                    file
            );
        }


        for (PackageNode child :
                node.packages()) {

            collectClassIndex(
                    child,
                    result
            );
        }
    }


    // =========================================================
    // Element Type Detection
    // =========================================================

    private ProjectElementType detectElementType(
            JavaFile file
    ) {

        Set<String> annotations =
                file.annotations()
                        .stream()
                        .map(
                                Annotation::name
                        )
                        .filter(
                                Objects::nonNull
                        )
                        .map(
                                String::toLowerCase
                        )
                        .collect(
                                Collectors.toSet()
                        );


        if (annotations.contains(
                "restcontroller"
        )
                || annotations.contains(
                "controller"
        )) {

            return ProjectElementType.CONTROLLER;
        }


        if (annotations.contains(
                "service"
        )) {

            return ProjectElementType.SERVICE;
        }


        if (annotations.contains(
                "repository"
        )) {

            return ProjectElementType.REPOSITORY;
        }


        if (annotations.contains(
                "entity"
        )) {

            return ProjectElementType.ENTITY;
        }


        if (annotations.contains(
                "configuration"
        )) {

            return ProjectElementType.CONFIGURATION;
        }


        if (annotations.contains(
                "component"
        )) {

            return ProjectElementType.COMPONENT;
        }


        String classType =
                String.valueOf(
                        file.classType()
                );


        return switch (classType) {

            case "INTERFACE" -> ProjectElementType.INTERFACE;

            case "ENUM" -> ProjectElementType.ENUM;

            case "RECORD" -> ProjectElementType.RECORD;

            default -> ProjectElementType.CLASS;
        };
    }


    private ProjectElementType resolveTargetType(
            String type,
            Map<String, JavaFile> classIndex
    ) {

        if (type == null
                || type.isBlank()) {

            return ProjectElementType.UNKNOWN;
        }


        JavaFile target =
                classIndex.get(
                        type
                );


        if (target != null) {

            return detectElementType(
                    target
            );
        }


        /*
         * اگر کلاس داخل Tree نبود،
         * naming فقط fallback است.
         */
        String lower =
                type.toLowerCase();


        if (lower.endsWith(
                "controller"
        )) {

            return ProjectElementType.CONTROLLER;
        }


        if (lower.endsWith(
                "service"
        )) {

            return ProjectElementType.SERVICE;
        }


        if (lower.endsWith(
                "repository"
        )) {

            return ProjectElementType.REPOSITORY;
        }


        if (lower.endsWith(
                "entity"
        )) {

            return ProjectElementType.ENTITY;
        }


        return ProjectElementType.UNKNOWN;
    }


    // =========================================================
    // Helpers
    // =========================================================

    private String formatParameters(
            List<Parameter> parameters
    ) {

        if (parameters == null
                || parameters.isEmpty()) {

            return "";
        }


        return parameters.stream()
                .map(parameter ->
                        parameter.type()
                                + " "
                                + parameter.name()
                )
                .collect(
                        Collectors.joining(", ")
                );
    }


    private String removeJavaExtension(
            String fileName
    ) {

        if (fileName == null) {
            return "";
        }


        if (fileName.endsWith(
                ".java"
        )) {

            return fileName.substring(
                    0,
                    fileName.length() - 5
            );
        }


        return fileName;
    }


    private String simpleType(
            String type
    ) {

        if (type == null
                || type.isBlank()) {

            return "";
        }


        String value =
                type.trim();


        /*
         * Array
         */
        value =
                value.replace(
                        "[]",
                        ""
                );


        /*
         * Generic:
         *
         * List<UserService>
         *
         * فعلاً UserService را برمی‌گردانیم.
         */
        int genericStart =
                value.indexOf('<');


        int genericEnd =
                value.lastIndexOf('>');


        if (genericStart >= 0
                && genericEnd > genericStart) {

            String genericType =
                    value.substring(
                            genericStart + 1,
                            genericEnd
                    );


            /*
             * در حالت چند Generic فعلاً
             * اولین Type کافی است.
             */
            int comma =
                    genericType.indexOf(',');


            if (comma >= 0) {

                genericType =
                        genericType.substring(
                                0,
                                comma
                        );
            }


            value =
                    genericType.trim();
        }


        /*
         * Qualified name -> simple name
         */
        int dot =
                value.lastIndexOf('.');


        if (dot >= 0) {

            value =
                    value.substring(
                            dot + 1
                    );
        }


        return value.trim();
    }


    private String safe(
            Object value
    ) {

        return value == null
                ? ""
                : String.valueOf(
                value
        );
    }
}