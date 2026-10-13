package com.lelentandane.synthesis.model;

/** An idea in the knowledge graph, such as "Feedback loop" in the discipline "Biology". */
public record Concept(long id, String name, String discipline, String description) {
}
