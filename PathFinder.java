package com.lelentandane.synthesis.graph;

import com.lelentandane.synthesis.graph.KnowledgeGraph.Edge;
import com.lelentandane.synthesis.model.Concept;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Answers "how are these two concepts connected?" three ways:
 *  - fewestSteps: breadth-first search, the route with the fewest hops
 *  - strongest: Dijkstra's algorithm where a stronger relation is a cheaper hop
 *  - alternatives: depth-first search for other simple routes, capped so it cannot run away
 */
public final class PathFinder {

    /** Relations are rated 1 to 5; a 5 costs 1 and a 1 costs 5, so Dijkstra prefers strong links. */
    static int cost(Edge edge) {
        return 6 - edge.relation().strength();
    }

    private static final int MAX_EXPANSIONS = 200_000;

    private final KnowledgeGraph graph;

    public PathFinder(KnowledgeGraph graph) {
        this.graph = graph;
    }

    /** Breadth-first search. Among equal-length routes, stronger relations are tried first. */
    public Optional<Path> fewestSteps(long fromId, long toId) {
        if (!graph.contains(fromId) || !graph.contains(toId)) {
            return Optional.empty();
        }
        Map<Long, Hop> cameFrom = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        visited.add(fromId);
        queue.add(fromId);

        while (!queue.isEmpty()) {
            long current = queue.poll();
            if (current == toId) {
                break;
            }
            for (Edge edge : graph.bestEdges(current)) {
                if (visited.add(edge.neighborId())) {
                    cameFrom.put(edge.neighborId(), new Hop(current, edge));
                    queue.add(edge.neighborId());
                }
            }
        }
        return rebuild(fromId, toId, cameFrom);
    }

    /** Dijkstra's algorithm. Ties on total cost are broken by fewer hops. */
    public Optional<Path> strongest(long fromId, long toId) {
        if (!graph.contains(fromId) || !graph.contains(toId)) {
            return Optional.empty();
        }
        record State(long id, int cost, int hops) {
        }
        Comparator<State> order = Comparator.comparingInt(State::cost).thenComparingInt(State::hops);
        PriorityQueue<State> queue = new PriorityQueue<>(order);
        Map<Long, int[]> best = new HashMap<>(); // id -> {cost, hops}
        Map<Long, Hop> cameFrom = new HashMap<>();
        best.put(fromId, new int[] {0, 0});
        queue.add(new State(fromId, 0, 0));

        while (!queue.isEmpty()) {
            State state = queue.poll();
            int[] known = best.get(state.id());
            if (state.cost() > known[0] || (state.cost() == known[0] && state.hops() > known[1])) {
                continue; // a better route to this concept was already found
            }
            if (state.id() == toId) {
                break;
            }
            for (Edge edge : graph.bestEdges(state.id())) {
                int newCost = state.cost() + cost(edge);
                int newHops = state.hops() + 1;
                int[] current = best.get(edge.neighborId());
                boolean better = current == null || newCost < current[0]
                        || (newCost == current[0] && newHops < current[1]);
                if (better) {
                    best.put(edge.neighborId(), new int[] {newCost, newHops});
                    cameFrom.put(edge.neighborId(), new Hop(state.id(), edge));
                    queue.add(new State(edge.neighborId(), newCost, newHops));
                }
            }
        }
        return rebuild(fromId, toId, cameFrom);
    }

    /**
     * Other routes between the two concepts, shortest first, never revisiting a concept.
     * Routes that pass through the same concepts as the excluded route are skipped.
     */
    public List<Path> alternatives(long fromId, long toId, int maxSteps, int limit, Path exclude) {
        if (!graph.contains(fromId) || !graph.contains(toId) || fromId == toId || limit <= 0) {
            return List.of();
        }
        List<List<Edge>> found = new ArrayList<>();
        int[] budget = {MAX_EXPANSIONS};
        Set<Long> onRoute = new HashSet<>();
        onRoute.add(fromId);
        explore(fromId, toId, maxSteps, onRoute, new ArrayList<>(), found, budget);

        List<Path> paths = new ArrayList<>();
        for (List<Edge> edges : found) {
            paths.add(toPath(fromId, edges));
        }
        Comparator<Path> order = Comparator
                .comparingInt(Path::length)
                .thenComparingInt(p -> -p.totalStrength())
                .thenComparing(p -> p.conceptIds().toString());
        paths.sort(order);

        List<Path> result = new ArrayList<>();
        for (Path path : paths) {
            if (exclude != null && path.conceptIds().equals(exclude.conceptIds())) {
                continue;
            }
            result.add(path);
            if (result.size() == limit) {
                break;
            }
        }
        return result;
    }

    private void explore(long current, long target, int stepsLeft, Set<Long> onRoute,
                         List<Edge> route, List<List<Edge>> found, int[] budget) {
        if (stepsLeft == 0 || budget[0] <= 0) {
            return;
        }
        for (Edge edge : graph.bestEdges(current)) {
            if (--budget[0] <= 0) {
                return;
            }
            long next = edge.neighborId();
            if (onRoute.contains(next)) {
                continue;
            }
            route.add(edge);
            if (next == target) {
                found.add(new ArrayList<>(route));
            } else {
                onRoute.add(next);
                explore(next, target, stepsLeft - 1, onRoute, route, found, budget);
                onRoute.remove(next);
            }
            route.remove(route.size() - 1);
        }
    }

    private record Hop(long previousId, Edge edge) {
    }

    private Optional<Path> rebuild(long fromId, long toId, Map<Long, Hop> cameFrom) {
        if (fromId == toId) {
            return Optional.of(new Path(graph.require(fromId), List.of()));
        }
        if (!cameFrom.containsKey(toId)) {
            return Optional.empty();
        }
        List<Edge> reversed = new ArrayList<>();
        long cursor = toId;
        while (cursor != fromId) {
            Hop hop = cameFrom.get(cursor);
            reversed.add(hop.edge());
            cursor = hop.previousId();
        }
        List<Edge> edges = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) {
            edges.add(reversed.get(i));
        }
        return Optional.of(toPath(fromId, edges));
    }

    private Path toPath(long fromId, List<Edge> edges) {
        List<Path.Step> steps = new ArrayList<>();
        Concept current = graph.require(fromId);
        for (Edge edge : edges) {
            Concept next = graph.require(edge.neighborId());
            steps.add(new Path.Step(current, next, edge.relation(), edge.forward()));
            current = next;
        }
        return new Path(graph.require(fromId), steps);
    }
}
