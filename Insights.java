package com.lelentandane.synthesis.graph;

import com.lelentandane.synthesis.model.Concept;
import com.lelentandane.synthesis.model.Relation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Whole-graph analysis: separate clusters, the concepts that hold the graph together,
 * how disciplines link to each other, and connections that look like they are missing.
 */
public final class Insights {

    public record Component(int size, List<Long> conceptIds, List<String> disciplines) {
    }

    public record Keystone(Concept concept, int degree, double betweenness) {
    }

    public record DisciplineLink(String first, String second, int count) {
    }

    public record Suggestion(Concept first, Concept second, List<Concept> shared, double score) {
    }

    private final KnowledgeGraph graph;

    public Insights(KnowledgeGraph graph) {
        this.graph = graph;
    }

    /** Groups of concepts that can reach each other, largest first. Found with breadth-first search. */
    public List<Component> components() {
        List<Component> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (Concept start : graph.concepts()) {
            if (visited.contains(start.id())) {
                continue;
            }
            List<Long> members = new ArrayList<>();
            Set<String> disciplines = new TreeSet<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(start.id());
            visited.add(start.id());
            while (!queue.isEmpty()) {
                long current = queue.poll();
                members.add(current);
                disciplines.add(graph.require(current).discipline());
                for (long neighbor : graph.neighborIds(current)) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            result.add(new Component(members.size(), members, new ArrayList<>(disciplines)));
        }
        result.sort(Comparator.comparingInt(Component::size).reversed()
                .thenComparingLong(c -> c.conceptIds().get(0)));
        return result;
    }

    /** Concepts with no relations at all. */
    public List<Concept> orphans() {
        List<Concept> result = new ArrayList<>();
        for (Concept concept : graph.concepts()) {
            if (graph.degree(concept.id()) == 0) {
                result.add(concept);
            }
        }
        return result;
    }

    /**
     * Betweenness centrality with Brandes' algorithm: for every pair of concepts, the share of
     * shortest routes that pass through each concept in between. High scores mark the ideas
     * that connect otherwise separate parts of the graph. Runs in O(V * E).
     */
    public Map<Long, Double> betweenness() {
        Map<Long, Double> score = new HashMap<>();
        for (Concept concept : graph.concepts()) {
            score.put(concept.id(), 0.0);
        }
        for (Concept source : graph.concepts()) {
            Deque<Long> stack = new ArrayDeque<>();
            Map<Long, List<Long>> predecessors = new HashMap<>();
            Map<Long, Double> pathCount = new HashMap<>();
            Map<Long, Integer> distance = new HashMap<>();
            for (Concept concept : graph.concepts()) {
                predecessors.put(concept.id(), new ArrayList<>());
                pathCount.put(concept.id(), 0.0);
                distance.put(concept.id(), -1);
            }
            pathCount.put(source.id(), 1.0);
            distance.put(source.id(), 0);
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(source.id());

            while (!queue.isEmpty()) {
                long v = queue.poll();
                stack.push(v);
                for (long w : graph.neighborIds(v)) {
                    if (distance.get(w) < 0) {
                        distance.put(w, distance.get(v) + 1);
                        queue.add(w);
                    }
                    if (distance.get(w) == distance.get(v) + 1) {
                        pathCount.put(w, pathCount.get(w) + pathCount.get(v));
                        predecessors.get(w).add(v);
                    }
                }
            }

            Map<Long, Double> dependency = new HashMap<>();
            for (Concept concept : graph.concepts()) {
                dependency.put(concept.id(), 0.0);
            }
            while (!stack.isEmpty()) {
                long w = stack.pop();
                for (long v : predecessors.get(w)) {
                    double share = (pathCount.get(v) / pathCount.get(w)) * (1.0 + dependency.get(w));
                    dependency.put(v, dependency.get(v) + share);
                }
                if (w != source.id()) {
                    score.put(w, score.get(w) + dependency.get(w));
                }
            }
        }
        // Every pair was counted from both ends in an undirected graph.
        score.replaceAll((id, value) -> value / 2.0);
        return score;
    }

    /** The concepts with the highest betweenness; those with a score of zero are left out. */
    public List<Keystone> keystones(int limit) {
        Map<Long, Double> scores = betweenness();
        List<Keystone> result = new ArrayList<>();
        for (Concept concept : graph.concepts()) {
            double value = scores.get(concept.id());
            if (value > 0) {
                result.add(new Keystone(concept, graph.degree(concept.id()), Math.round(value * 100) / 100.0));
            }
        }
        result.sort(Comparator.comparingDouble(Keystone::betweenness).reversed()
                .thenComparing(Comparator.comparingInt(Keystone::degree).reversed())
                .thenComparing(k -> k.concept().name()));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    /** How many relations cross between each pair of disciplines, busiest first. */
    public List<DisciplineLink> disciplineLinks() {
        Map<String, Integer> counts = new TreeMap<>();
        Set<Long> seen = new HashSet<>();
        for (Concept concept : graph.concepts()) {
            for (KnowledgeGraph.Edge edge : graph.edges(concept.id())) {
                Relation relation = edge.relation();
                if (!seen.add(relation.id())) {
                    continue;
                }
                String a = graph.require(relation.fromId()).discipline();
                String b = graph.require(relation.toId()).discipline();
                if (a.equalsIgnoreCase(b)) {
                    continue;
                }
                String first = a.compareToIgnoreCase(b) <= 0 ? a : b;
                String second = first.equals(a) ? b : a;
                counts.merge(first + "\u0000" + second, 1, Integer::sum);
            }
        }
        List<DisciplineLink> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String[] names = entry.getKey().split("\u0000");
            result.add(new DisciplineLink(names[0], names[1], entry.getValue()));
        }
        result.sort(Comparator.comparingInt(DisciplineLink::count).reversed()
                .thenComparing(DisciplineLink::first).thenComparing(DisciplineLink::second));
        return result;
    }

    /**
     * Pairs of concepts that are not linked but share at least two neighbours. The score is the
     * Jaccard similarity of their neighbour sets: shared neighbours divided by all neighbours.
     * Concepts that surround the same ideas probably belong together.
     */
    public List<Suggestion> suggestions(int limit) {
        List<Concept> all = new ArrayList<>(graph.concepts());
        all.sort(Comparator.comparingLong(Concept::id));
        Map<Long, Set<Long>> neighbors = new HashMap<>();
        for (Concept concept : all) {
            neighbors.put(concept.id(), graph.neighborIds(concept.id()));
        }

        List<Suggestion> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Concept a = all.get(i);
                Concept b = all.get(j);
                Set<Long> na = neighbors.get(a.id());
                Set<Long> nb = neighbors.get(b.id());
                if (na.contains(b.id())) {
                    continue; // already linked
                }
                Set<Long> shared = new LinkedHashSet<>(na);
                shared.retainAll(nb);
                if (shared.size() < 2) {
                    continue;
                }
                Set<Long> union = new HashSet<>(na);
                union.addAll(nb);
                List<Concept> sharedConcepts = new ArrayList<>();
                for (long id : shared) {
                    sharedConcepts.add(graph.require(id));
                }
                sharedConcepts.sort(Comparator.comparing(Concept::name));
                double score = (double) shared.size() / union.size();
                result.add(new Suggestion(a, b, sharedConcepts, Math.round(score * 100) / 100.0));
            }
        }
        result.sort(Comparator.comparingDouble(Suggestion::score).reversed()
                .thenComparing(Comparator.comparingInt((Suggestion s) -> s.shared().size()).reversed())
                .thenComparing(s -> s.first().name()).thenComparing(s -> s.second().name()));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }
}
