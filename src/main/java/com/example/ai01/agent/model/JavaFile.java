package com.example.ai01.agent.model;

import java.util.List;

public record JavaFile(String fileName,
                       String path,
                       String packageName,
                       List<String> imports,          // برای قوانین وابستگی
                       ClassType classType,
                       List<Annotation> annotations,  // یکسان با Field/Method
                       String superClass,             // nullable
                       List<String> interfaces,
                       List<Field> fields,
                       List<Constructor> constructors,
                       List<Method> methods,
                       long size,
                       long lineCount) { }