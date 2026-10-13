package com.lelentandane.synthesis.model;

/**
 * A named, directed link between two concepts, for example
 * "Feedback loop" --drives--> "Compounding". Strength runs from 1 (loose) to 5 (very close).
 * Searches treat links as two-way, but the stored direction is used to phrase explanations.
 */
public record Relation(long id, long fromId, long toId, String label, int strength) {
}
