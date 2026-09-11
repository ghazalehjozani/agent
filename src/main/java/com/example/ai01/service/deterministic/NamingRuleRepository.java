package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.Annotation;
import com.example.ai01.agent.model.Field;
import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;
import com.example.ai01.agent.model.Parameter;
import com.example.ai01.agent.model.ViolationFinding;
import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class NamingRuleRepository extends AbstractRuleRepository {
    private static final Set<String> PARTIAL_RESPONSE_PARAMETERS = Set.of("fields", "view", "expand");
    private final NamingProjectInspector inspector;
    private final Map<String, NamingRuleCheck> checks;

    public NamingRuleRepository(
            SourceTextProvider sourceTextProvider,
            NamingProjectInspector inspector) {
        super(sourceTextProvider);
        this.inspector = inspector;
        this.checks = createChecks();
    }

    @Override
    public String strategyKey() {
        return "NAME";
    }

    @Override
    public boolean supports(String code) {
        String normalized = RuleCode.normalize(code);
        return checks.keySet().stream().anyMatch(normalized::endsWith);
    }

    @Override
    public List<ViolationFinding> evaluate(ArchitectureRule rule, PackageNode project) {
        String normalized = RuleCode.normalize(rule.id());
        NamingRuleCheck check = checks.entrySet().stream()
                .filter(entry -> normalized.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No naming check is implemented for rule " + rule.id()
                ));
        return check.evaluate(rule, inspector.inspect(project));
    }

    private Map<String, NamingRuleCheck> createChecks() {
        Map<String, NamingRuleCheck> result = new LinkedHashMap<>();
        result.put("12004NAME001", this::controllerSuffix);
        result.put("12005NAME002", this::serviceSuffix);
        result.put("12006NAME003", this::repositorySuffix);
        result.put("02004URL001", this::urlSegmentsKebabCase);
        result.put("02010URL007", this::resourcePlurality);
        result.put("02015API004", this::queryParametersCamelCase);
        result.put("03031API003", this::issuerUppercase);
        result.put("04002DATA002", this::jsonFieldsCamelCase);
        result.put("04018DATA007", this::jalaliFieldExplicitlyNamed);
        result.put("04035DATA002", this::enumCodesUpperSnakeCase);
        result.put("04040DATA007", this::arrayFieldsPlural);
        result.put("06005API005", this::partialResponseParameterNames);
        result.put("06007API002", this::selectedFieldsMatchSchema);
        result.put("06008API003", this::nestedFieldsUseDot);
        result.put("06020API001", this::namedViewsUppercase);
        result.put("06028API001", this::expandNamesCamelCase);
        result.put("07009NAME001", this::channelPattern);
        result.put("07010NAME002", this::channelPrefix);
        result.put("07011NAME003", this::channelSystem);
        result.put("07012NAME004", this::channelDomain);
        result.put("07013NAME005", this::channelComponent);
        result.put("07014NAME006", this::eventName);
        result.put("07015NAME007", this::commandName);
        result.put("07016NAME008", this::requestResponseQualifier);
        result.put("07017NAME009", this::channelTransport);
        result.put("07018NAME010", this::channelVersion);
        result.put("07019NAME011", this::deadLetterSuffix);
        result.put("08001SECURITY001", this::standardHeaderNames);
        result.put("08009SECURITY008", this::authorizationHeaderName);
        return Map.copyOf(result);
    }

    private List<ViolationFinding> controllerSuffix(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return classSuffix(rule, data, inspector::isController, "Controller");
    }

    private List<ViolationFinding> serviceSuffix(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return classSuffix(rule, data, inspector::isService, "Service");
    }

    private List<ViolationFinding> repositorySuffix(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return classSuffix(rule, data, inspector::isRepository, "Repository");
    }

    private List<ViolationFinding> classSuffix(
            ArchitectureRule rule,
            NamingProjectInspector.Inspection data,
            java.util.function.Predicate<JavaFile> applicable,
            String suffix) {
        return data.files().stream()
                .filter(applicable)
                .filter(file -> !inspector.className(file).endsWith(suffix))
                .map(file -> violation(rule, file.path(), null,
                        "Class " + inspector.className(file) + " does not end with " + suffix,
                        "Rename the class so its name ends with " + suffix + "."))
                .toList();
    }

    private List<ViolationFinding> urlSegmentsKebabCase(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return namedValueViolations(rule, data.mappings(), value ->
                        inspector.pathSegments(value).stream().allMatch(inspector::isKebabCase),
                "URL contains a segment that is not kebab-case.");
    }

    private List<ViolationFinding> resourcePlurality(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return namedValueViolations(rule, data.mappings(), this::hasValidResourcePlurality,
                "A collection resource name must be plural before a resource identifier.");
    }

    private boolean hasValidResourcePlurality(String path) {
        String[] segments = path.split("/");
        for (int index = 0; index + 1 < segments.length; index++) {
            if (!segments[index].isBlank() && segments[index + 1].matches("\\{[^}]+}")) {
                if (!looksPlural(segments[index])) return false;
            }
        }
        return true;
    }

    private List<ViolationFinding> queryParametersCamelCase(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return namedValueViolations(rule, data.queryParameters(), inspector::isCamelCase,
                "Query parameter name is not camelCase.");
    }

    private List<ViolationFinding> issuerUppercase(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return literalViolations(rule, data, "issuer", value -> value.matches("[A-Z]{2,6}"),
                "Issuer value must contain 2-6 uppercase characters.");
    }

    private List<ViolationFinding> jsonFieldsCamelCase(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        List<ViolationFinding> result = new ArrayList<>();
        for (JavaFile file : data.files()) {
            for (Field field : file.fields()) {
                if (!NamingSourceRules.isSerializableField(data.sources().get(file.path()), field)) continue;
                String name = effectiveJsonName(field);
                if (!inspector.isCamelCase(name)) result.add(violation(rule, file.path(), null,
                        "JSON field name is not camelCase: " + name,
                        "Rename the field or its @JsonProperty value to camelCase."));
            }
        }
        return List.copyOf(result);
    }

    private List<ViolationFinding> jalaliFieldExplicitlyNamed(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return List.of(); // A local name alone cannot prove that a value is Jalali.
    }

    private List<ViolationFinding> enumCodesUpperSnakeCase(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return List.of(); // Enum response codes require serializer/OpenAPI inspection.
    }

    private List<ViolationFinding> arrayFieldsPlural(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        List<ViolationFinding> result = new ArrayList<>();
        for (JavaFile file : data.files()) {
            for (Field field : file.fields()) {
                if (isArrayOrCollection(field.type()) && !looksPlural(effectiveJsonName(field))) {
                    result.add(violation(rule, file.path(), null,
                            "Array or collection field is not plural: " + effectiveJsonName(field),
                            "Use a plural field name for arrays and collections."));
                }
            }
        }
        return List.copyOf(result);
    }

    private List<ViolationFinding> partialResponseParameterNames(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        Set<String> forbiddenAliases = Set.of("select", "include", "projection");
        return namedValueViolations(rule, data.queryParameters(), value -> !forbiddenAliases.contains(value),
                "Use only fields, view, or expand for partial-response parameters.");
    }

    private List<ViolationFinding> selectedFieldsMatchSchema(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return List.of(); // Runtime parameter values must be checked against the OpenAPI schema.
    }

    private List<ViolationFinding> nestedFieldsUseDot(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return List.of(); // Applies to runtime values of the fields parameter.
    }

    private List<ViolationFinding> namedViewsUppercase(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return literalViolations(rule, data, "view", value -> value.matches("[A-Z][A-Z0-9_]*"),
                "Named view value must be uppercase.");
    }

    private List<ViolationFinding> expandNamesCamelCase(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return List.of(); // Applies to runtime values of the expand parameter.
    }

    private List<ViolationFinding> channelPattern(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return namedValueViolations(rule, data.channels(), this::isChannelShapeValid,
                "Message channel does not follow the standard channel pattern.");
    }

    private List<ViolationFinding> channelPrefix(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return channelSegment(rule, data, 0, value -> value.equals("corridor"), "Channel prefix must be corridor.");
    }

    private List<ViolationFinding> channelSystem(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return channelSegment(rule, data, 1, inspector::isKebabCase, "Channel system name is invalid.");
    }

    private List<ViolationFinding> channelDomain(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return channelSegment(rule, data, 2, inspector::isKebabCase, "Channel domain name is invalid.");
    }

    private List<ViolationFinding> channelComponent(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return List.of(); // Component presence depends on domain architecture metadata.
    }

    private List<ViolationFinding> eventName(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return channelAction(rule, data, "event", value -> inspector.isKebabCase(value) && looksPastTense(value),
                "Event name must be past tense and kebab-case.");
    }

    private List<ViolationFinding> commandName(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return channelAction(rule, data, "command", this::looksImperative,
                "Command name must start with an approved imperative verb.");
    }

    private List<ViolationFinding> requestResponseQualifier(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return namedValueViolations(rule, data.channels(), value -> {
            List<String> segments = List.of(value.toLowerCase(Locale.ROOT).split("\\."));
            return (!segments.contains("request") && !segments.contains("response")) || segments.contains("command");
        }, "request/response qualifier is only allowed for command channels.");
    }

    private List<ViolationFinding> channelTransport(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return namedValueViolations(rule, data.channels(), value ->
                        value.matches("(?i).*(?:^|\\.)(topic|queue)(?:\\.|$).*"),
                "Channel name must include topic or queue.");
    }

    private List<ViolationFinding> channelVersion(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return namedValueViolations(rule, data.channels(), value -> value.matches("(?i).*\\.v[1-9][0-9]*(?:\\.dlt)?$"),
                "Channel version must use v followed by an integer greater than zero.");
    }

    private List<ViolationFinding> deadLetterSuffix(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return List.of(); // DLT intent cannot be inferred only from a channel literal.
    }

    private List<ViolationFinding> standardHeaderNames(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        return List.of(); // Header catalogue is external standard data, not a naming heuristic.
    }

    private List<ViolationFinding> authorizationHeaderName(ArchitectureRule rule, NamingProjectInspector.Inspection data) {
        List<ViolationFinding> result = new ArrayList<>();
        data.sources().forEach((path, source) -> NamingSourceRules.authorizationHeaders(path, source).stream()
                .filter(header -> !header.value().equals("Authorization"))
                .forEach(header -> result.add(violation(rule, path, header.line(),
                        "Authorization header has non-standard casing: " + header.value(),
                        "Authorization header must use the exact standard casing."))));
        return List.copyOf(result);
    }

    private List<ViolationFinding> channelSegment(
            ArchitectureRule rule, NamingProjectInspector.Inspection data, int index,
            java.util.function.Predicate<String> valid, String evidence) {
        return namedValueViolations(rule, data.channels(), value -> {
            String[] segments = value.split("\\.");
            return segments.length > index && valid.test(segments[index]);
        }, evidence);
    }

    private List<ViolationFinding> channelAction(
            ArchitectureRule rule, NamingProjectInspector.Inspection data, String kind,
            java.util.function.Predicate<String> valid, String evidence) {
        return namedValueViolations(rule, data.channels(), value -> {
            List<String> segments = List.of(value.toLowerCase(Locale.ROOT).split("\\."));
            int kindIndex = segments.indexOf(kind);
            return kindIndex < 0 || (kindIndex > 0 && valid.test(segments.get(kindIndex - 1)));
        }, evidence);
    }

    private boolean isChannelShapeValid(String value) {
        return value.matches("[a-z0-9-]+(?:\\.[a-z0-9-]+){4,}(?:\\.dlt)?")
                && value.matches(".*\\.(event|command)\\..*")
                && value.matches(".*\\.(topic|queue)\\.v[1-9][0-9]*(?:\\.dlt)?$");
    }

    private boolean looksImperative(String value) {
        String first = value.split("-")[0];
        return Set.of("create", "update", "delete", "get", "fetch", "send", "process", "approve",
                "reject", "cancel", "start", "stop", "validate", "execute", "generate", "publish").contains(first);
    }

    private boolean looksPastTense(String value) {
        String first = value.split("-")[0];
        return first.endsWith("ed") || Set.of("sent", "paid", "done", "created", "updated", "deleted").contains(first);
    }

    private boolean isArrayOrCollection(String type) {
        return type != null && (type.endsWith("[]") || type.matches(".*\\b(List|Set|Collection|Iterable)\\s*<.*"));
    }

    private boolean looksPlural(String value) {
        return value != null && (value.endsWith("s") || value.endsWith("ies"));
    }

    private String effectiveJsonName(Field field) {
        return field.annotations().stream()
                .filter(annotation -> annotation.name().equals("JsonProperty")
                        || annotation.name().endsWith(".JsonProperty"))
                .map(Annotation::attributes)
                .map(attributes -> attributes.getOrDefault("value", attributes.get("name")))
                .filter(java.util.Objects::nonNull)
                .map(value -> value.replaceAll("^[\"']|[\"']$", ""))
                .findFirst()
                .orElse(field.name());
    }

    private List<ViolationFinding> namedValueViolations(
            ArchitectureRule rule, List<NamingProjectInspector.NamedValue> values,
            java.util.function.Predicate<String> valid, String evidence) {
        return values.stream().filter(value -> !valid.test(value.value()))
                .map(value -> violation(rule, value.file(), value.line(), evidence + " Value: " + value.value(),
                        "Rename the value according to the architecture naming standard."))
                .toList();
    }

    private List<ViolationFinding> literalViolations(
            ArchitectureRule rule, NamingProjectInspector.Inspection data, String marker,
            java.util.function.Predicate<String> valid, String evidence) {
        List<ViolationFinding> result = new ArrayList<>();
        data.sources().forEach((path, source) -> {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(source);
            while (matcher.find()) {
                String value = matcher.group(1);
                if (value.toLowerCase(Locale.ROOT).contains(marker) && !valid.test(value)) {
                    result.add(violation(rule, path, line(source, matcher.start()), evidence + " Value: " + value,
                            "Use the exact naming and casing required by the standard."));
                }
            }
        });
        return List.copyOf(result);
    }

    private int line(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) if (source.charAt(index) == '\n') line++;
        return line;
    }

    private ViolationFinding violation(
            ArchitectureRule rule, String file, Integer line, String evidence, String recommendation) {
        return new ViolationFinding(rule.id(), "naming", rule.id(), "MAJOR", file, line,
                evidence, recommendation, 1.0);
    }

    @FunctionalInterface
    private interface NamingRuleCheck {
        List<ViolationFinding> evaluate(ArchitectureRule rule, NamingProjectInspector.Inspection data);
    }
}
