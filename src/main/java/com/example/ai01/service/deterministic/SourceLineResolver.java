package com.example.ai01.service.deterministic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SourceLineResolver {
    private SourceLineResolver() { }

    static Integer methodDeclaration(String source, String methodName) {
        if (source == null || source.isBlank() || methodName == null || methodName.isBlank()) return null;
        Pattern declaration = Pattern.compile(
                "(?m)^[^\\n]*(?:public|protected|private)\\s+[^\\n]*\\b"
                        + Pattern.quote(methodName) + "\\s*\\("
        );
        Matcher matcher = declaration.matcher(source);
        if (!matcher.find()) return null;
        return lineAt(source, matcher.start());
    }

    private static int lineAt(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') line++;
        }
        return line;
    }
}
