package com.example.ai01.dto;

import java.util.List;

public class AnalysisRequest {

    private List<String> architectureDoc;

    private String projectPath;

    public List<String> getArchitectureDoc() {
        return architectureDoc;
    }

    public void setArchitectureDoc(List<String> architectureDoc) {
        this.architectureDoc = architectureDoc;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

}