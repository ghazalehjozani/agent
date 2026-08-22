package com.example.ai01.agent.model;

import java.util.List;
public record PackageNode(String name,               // "service"
                          String qualifiedName,      // "com.example.app.service"
                          List<PackageNode> packages,
                          List<JavaFile> javaFiles) {
    public PackageNode {
        packages = packages == null ? List.of() : List.copyOf(packages);
        javaFiles = javaFiles == null ? List.of() : List.copyOf(javaFiles);
    }
}