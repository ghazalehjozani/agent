package com.example.ai01.agent.model;

import org.apache.tomcat.util.bcel.classfile.JavaClass;

import java.util.List;
public record Method(String name,
                     AccessModifier accessModifier,
                     List<Annotation> annotations,
                     String returnType,
                     List<Parameter> parameters,
                     boolean isStatic) { }