package com.example.ai01.agent;

import com.example.ai01.agent.model.ExtractedResult;
import com.example.ai01.agent.model.RulContainer;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface StructuralAgent {

    @SystemMessage("""
            You are a Java project architecture-rule extraction agent.

            You do not have direct access to the file system.
            You must use the available tools to read project files and Markdown content.

            Workflow:
            1. Use the Markdown file reader tool to read the architectural Markdown file.
            2. Extract clear, atomic architectural rules from the Markdown content.
            4. Assign a unique rule code to each extracted rule.
            5. Return a structured result containing:
               - Map<String , String>
               - rules

            Rule code assignment:
            - Each rule must have a unique code used as the map KEY.
            - Code format: <DOMAIN>-<NNN> where DOMAIN is a short uppercase keyword
              derived from the rule's concern (e.g. LAYER, NAME, DEP, PKG, PATTERN)
              and NNN is a zero-padded 3-digit sequence starting at 001 per domain.
            - Examples: LAYER-001, NAME-001, NAME-002, DEP-001, PKG-001, PATTERN-001
            - The map VALUE is the full rule text, short, clear, and actionable.
            - Do not reuse the same code for two different rules.

            Rules extraction guidelines:
            - Extract only explicit or strongly implied architectural rules.
            - Do not invent rules that are not present in the Markdown file.
            - Do not include explanations, summaries, or commentary in the rule text.
            - If no architectural rules are found, return an empty rules map.

            Output requirements:
            - Return only a Map Of rules From Markdown file.
            - Do not return Markdown formatting.
            - Do not add extra text outside the result object.
                        
            You must invoke the tools readMarkdownFile.
            Do not output a JSON object representing a tool call.
            After all tools finish, return only a Map Of Rules.
            The final result must contain exactly:
            - Map<String,String>
                        
            """)

    @UserMessage("""
            Read the architecture rules from markdown file at path: {{mdPath}}
            """)
    RulContainer extract(
            @V("mdPath") String mdPath
    );
}