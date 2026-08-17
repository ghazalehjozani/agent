package com.example.ai01.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.workspace")
public class PathProperties {

    @Value("root-path")
    private String rootPath;

    @Value("architecture-result-md-file")
    private String ruleResultMap;

    @Value("working-space")
    private String workingSpace;

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getRuleResultMap() {
        return ruleResultMap;
    }

    public void setRuleResultMap(String ruleResultMap) {
        this.ruleResultMap = ruleResultMap;
    }

    public String getWorkingSpace() {
        return workingSpace;
    }

    public void setWorkingSpace(String workingSpace) {
        this.workingSpace = workingSpace;
    }
}
