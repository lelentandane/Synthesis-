package com.lelentandane.synthesis.graph;

import com.lelentandane.synthesis.model.Concept;
import com.lelentandane.synthesis.model.Relation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * An in-memory adjacency-list view of the concepts and relations.
 *
 * Relations are stored with a direction but searched as two-way links. For every concept the
 * edges are sorted strongest first, then by neighbour id, so every algorithm gives the same
 * answer on every run.
 */
public final class KnowledgeGraph {

    /** One end of a relation as seen from a concept. forward is true if the relation points away from it. */
    public record Edge(Relation relation, long neighborId, boolean forward) {
    }

    private static final Comparator<Edge> STRONGEST_FIRST = Comparator
            .comparingInt((Edge e) -> -e.relation().strength())
            .thenComparingLong(Edge::neighborId)
            .thenComparingLong(e -> e.relation().id());

    private final Map<Long, Concept> concepts = new LinkedHashMap<>();
    private final Map<Long, List<Edge>> edges = new HashMap<>();
    private final Map<Long, List<Edge>> bestEdges = new HashMap<>();
    private int relationCount;

    public KnowledgeGraph(Collection<Concept> allConcepts, Collection<Relation> allRelations) {
        for (Concept concept : allConcepts) {
            concepts.put(concept.id(), concept);
            edges.put(concept.id(), new ArrayList<>());
        }
        for (Relation relation : allRelations) {
            if (!concepts.containsKey(relation.fromId()) || !concepts.containsKey(relation.toId())) {
                continue; // a dangling relation cannot be traversed
            }
            edges.get(relation.fromId()).add(new Edge(relation, relation.toId(), true));
            edges.get(relation.toId()).add(new Edge(relation, relation.fromId(), false));
            relationCount++;
        }
        for (Map.Entry<Long, List<Edge>> entry : edges.entrySet()) {
            entry.getValue().sort(STRONGEST_FIRST);
            bestEdges.put(entry.getKey(), pickStrongestPerNeighbor(entry.getValue()));
        }
    }

    /** Keeps only the strongest relation to each distinct neighbour. */
    private static List<Edge> pickStrongestPerNeighbor(List<Edge> sorted) {
        Set<Long> seen = new LinkedHashSet<>();
        List<Edge> result = new ArrayList<>();
        for (Edge edge : sorted) {
            if (seen.add(edge.neighborId())) {
                result.add(edge);
            }
        }
        return result;
    }

    public Optional<Concept> concept(long id) {
        return Optional.ofNullable(concepts.get(id));
    }

    public Concept require(long id) {
        Concept concept = concepts.get(id);
        if (concept == null) {
            throw new NoSuchElementException("No concept with id " + id);
        }
        return concept;
    }

    public Collection<Concept> concepts() {
        return concepts.values();
    }

    public boolean contains(long id) {
        return concepts.containsKey(id);
    }

    /** Every relation touching the concept, strongest first. */
    public List<Edge> edges(long id) {
        return edges.getOrDefault(id, List.of());
    }

    /** One edge per distinct neighbour (the strongest). Used by the path and analysis algorithms. */
    public List<Edge> bestEdges(long id) {
        return bestEdges.getOrDefault(id, List.of());
    }

    /** Number of relations touching the concept. */
    public int degree(long id) {
        return edges(id).size();
    }

    public Set<Long> neighborIds(long id) {
        Set<Long> result = new LinkedHashSet<>();
        for (Edge edge : bestEdges(id)) {
            result.add(edge.neighborId());
        }
        return result;
    }

    public int conceptCount() {
        return concepts.size();
    }

    public int relationCount() {
        return relationCount;
    }
}
