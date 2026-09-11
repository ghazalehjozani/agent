package com.example.ai01.service.deterministic;

import com.example.ai01.agent.model.JavaFile;
import com.example.ai01.agent.model.PackageNode;

import java.util.Map;

public interface SourceTextProvider {

    String read(JavaFile javaFile);

    Map<String, String> readAll(PackageNode project);
}
