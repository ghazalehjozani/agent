package com.example.ai01.tools;

import com.example.ai01.agent.model.Parameter;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.example.ai01.agent.model.*;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Component
public class ProjectStructureExtractor {
    public PackageNode readJavaFiles(String rootPath) {
        Path root = Path.of(rootPath);

        // packageName -> فایل‌های آن پکیج
        Map<String, List<JavaFile>> byPackage = new TreeMap<>();

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("target")
                            && !p.toString().contains("build"))
                    .forEach(p -> parse(p).ifPresent(jf ->
                            byPackage.computeIfAbsent(jf.packageName(), k -> new ArrayList<>())
                                    .add(jf)));
        } catch (IOException exception) {
            //TODO handle this exception properly
            return new PackageNode("", "", List.of(), List.of());
        }
        return buildTree("", "", byPackage);
    }

    private Optional<JavaFile> parse(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);

            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString()).orElse("");

            List<String> imports = cu.getImports().stream()
                    .map(i -> i.getNameAsString()).toList();

            // نوع اصلی فایل (کلاس/اینترفیس/enum/record هم‌نام فایل)
            TypeDeclaration<?> type = cu.getPrimaryType()
                    .orElse(cu.getTypes().isEmpty() ? null : cu.getType(0));
            if (type == null) return Optional.empty(); // مثلاً package-info.java

            return Optional.of(new JavaFile(
                    path.getFileName().toString(),
                    path.toString(),
                    packageName,
                    imports,
                    classTypeOf(type),
                    annotationsOf(type.getAnnotations()),
                    superClassOf(type),
                    interfacesOf(type),
                    fieldsOf(type),
                    constructorsOf(type),
                    methodsOf(type),
                    Files.size(path),
                    cu.getRange().map(r -> (long) r.end.line).orElse(0L)));
        } catch (Exception e) {
            return Optional.empty(); // فایل غیرقابل‌parse اسکن را متوقف نکند
        }
    }

    private ClassType classTypeOf(TypeDeclaration<?> t) {
        if (t.isEnumDeclaration()) return ClassType.ENUM;
        if (t.isRecordDeclaration()) return ClassType.RECORD;
        if (t.isAnnotationDeclaration()) return ClassType.ANNOTATION;
        if (t.isClassOrInterfaceDeclaration()
                && t.asClassOrInterfaceDeclaration().isInterface())
            return ClassType.INTERFACE;
        return ClassType.CLASS;
    }

    private String superClassOf(TypeDeclaration<?> t) {
        if (!t.isClassOrInterfaceDeclaration()) return null;
        return t.asClassOrInterfaceDeclaration().getExtendedTypes().stream()
                .findFirst().map(Object::toString).orElse(null);
    }

    private List<String> interfacesOf(TypeDeclaration<?> t) {
        if (!t.isClassOrInterfaceDeclaration()) return List.of();
        return t.asClassOrInterfaceDeclaration().getImplementedTypes().stream()
                .map(Object::toString).toList();
    }

    private List<Field> fieldsOf(TypeDeclaration<?> t) {
        return t.getFields().stream()
                .flatMap(f -> f.getVariables().stream().map(v -> new Field(
                        annotationsOf(f.getAnnotations()),
                        accessOf(f),
                        v.getTypeAsString(),
                        v.getNameAsString())))
                .toList();
    }

    private List<Constructor> constructorsOf(TypeDeclaration<?> t) {
        return t.getConstructors().stream()
                .map(c -> new Constructor(
                        accessOf(c),
                        annotationsOf(c.getAnnotations()),
                        parametersOf(c.getParameters())))
                .toList();
    }

    private List<Method> methodsOf(TypeDeclaration<?> t) {
        return t.getMethods().stream()
                .map(m -> new Method(
                        m.getNameAsString(),
                        accessOf(m),
                        annotationsOf(m.getAnnotations()),
                        m.getTypeAsString(),
                        parametersOf(m.getParameters()),
                        m.isStatic()))
                .toList();
    }

    private List<Parameter> parametersOf(
            com.github.javaparser.ast.NodeList<com.github.javaparser.ast.body.Parameter> params) {
        return params.stream()
                .map(p -> new Parameter(
                        p.getTypeAsString(),
                        p.getNameAsString(),
                        annotationsOf(p.getAnnotations())
                ))
                .toList();
    }

    private List<Annotation> annotationsOf(
            com.github.javaparser.ast.NodeList<com.github.javaparser.ast.expr.AnnotationExpr> anns) {
        return anns.stream().map(a -> {
            Map<String, String> values = new LinkedHashMap<>();
            if (a.isSingleMemberAnnotationExpr())
                values.put("value", a.asSingleMemberAnnotationExpr()
                        .getMemberValue().toString());
            else if (a.isNormalAnnotationExpr())
                a.asNormalAnnotationExpr().getPairs().forEach(p ->
                        values.put(p.getNameAsString(), p.getValue().toString()));
            return new Annotation(a.getNameAsString(), values);
        }).toList();
    }

    private AccessModifier accessOf(BodyDeclaration<?> d) {
        var n = (NodeWithModifiers<?>) d;
        if (n.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC)) return AccessModifier.PUBLIC;
        if (n.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PRIVATE)) return AccessModifier.PRIVATE;
        if (n.hasModifier(com.github.javaparser.ast.Modifier.Keyword.PROTECTED)) return AccessModifier.PROTECTED;
        return AccessModifier.PACKAGE;
    }

    private PackageNode buildTree(String name, String qualified,
                                  Map<String, List<JavaFile>> byPackage) {
        String prefix = qualified.isEmpty() ? "" : qualified + ".";

        Set<String> childNames = new TreeSet<>();
        for (String pkg : byPackage.keySet()) {
            if (pkg.equals(qualified) || !pkg.startsWith(prefix)) continue;
            String rest = pkg.substring(prefix.length());
            childNames.add(rest.contains(".")
                    ? rest.substring(0, rest.indexOf('.')) : rest);
        }

        List<PackageNode> children = childNames.stream()
                .map(c -> buildTree(c, prefix + c, byPackage))
                .toList();

        return new PackageNode(name, qualified, children,
                byPackage.getOrDefault(qualified, List.of()));
    }
}
