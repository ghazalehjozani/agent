package com.example.ai01.service;

import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {
    private RulesEmbeddingService rulesEmbeddingService;
    private ProjectEmbeddingService projectEmbeddingService;

    public EmbeddingService(RulesEmbeddingService rulesEmbeddingService, ProjectEmbeddingService projectEmbeddingService) {
        this.rulesEmbeddingService = rulesEmbeddingService;
        this.projectEmbeddingService = projectEmbeddingService;
    }


    public void store() {




    }





}
