package com.example.ai01.tools;

import com.example.ai01.agent.model.ruleextraction.ArchitectureRule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class Utility {
    private Utility(MeterRegistry meterRegistry) {
    }

    public static String loadFile(String filePath) {
        Path path = Path.of(filePath);
        String content = "";
        try {
            String fileName =
                    path.getFileName().toString();
            content =
                    Files.readString(
                            path,
                            StandardCharsets.UTF_8
                    );

        } catch (IOException exception) {
            //
            content = "error";
        }
        return content;
    }

    public static String formatRules(List<ArchitectureRule> rules) {

        return rules.stream()
                .map(rule ->
                        "- " + rule.id()
                                + ": " + rule.description())
                .collect(Collectors.joining("\n"));
    }

    public static boolean writeDownRules(Map<String, String> rules, String path) {

        List<String> rs = new ArrayList();

        rs.add("<div dir=\"ltr\" style=\"text-align: right;\">");

        var fileContent = rules
                .keySet()
                .stream()
                .map((key) -> String.format("%s : %s \n", key, rules.get(key)))
                .collect(Collectors.toList());

        rs.addAll(fileContent);
        rs.add("</div>");

        var pt = Path.of(path);

        try {
            Files.write(pt, fileContent, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.out.println(ex
                    .getMessage());
            return false;
        }

        return true;
    }
}