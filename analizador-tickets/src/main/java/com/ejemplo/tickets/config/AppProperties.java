package com.ejemplo.tickets.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracion "app.*" del application.properties */
@ConfigurationProperties(prefix = "app")
public record AppProperties(N8n n8n) {

    /** URL del webhook de n8n que realiza el RAG (embeddings + Qdrant + LLM) */
    public record N8n(String webhookUrl) { }
}
