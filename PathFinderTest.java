package com.lelentandane.synthesis.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PathFinderTest {

    private PathFinder finder;

    /**
     * A --3-- B --3-- C --3-- D     (the strong, long way: 3 steps)
     * A --1-- E --1-- D             (the weak, short way: 2 steps)
     * F is not connected to anything.
     */
    @BeforeEach
    void setUp() {
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "A").concept(2, "B").concept(3, "C").concept(4, "D").concept(5, "E").concept(6, "F")
                .link(1, 2, "leads to", 3).link(2, 3, "feeds", 3).link(3, 4, "becomes", 3)
                .link(1, 5, "brushes", 1).link(5, 4, "touches", 1)
                .build();
        finder = new PathFinder(graph);
    }

    @Test
    void fewestStepsTakesTheShortRoute() {
        Path path = finder.fewestSteps(1, 4).orElseThrow();

        assertEquals(List.of(1L, 5L, 4L), path.conceptIds());
        assertEquals(2, path.length());
    }

    @Test
    void strongestTakesTheStrongerRouteEvenIfLonger() {
        Path path = finder.strongest(1, 4).orElseThrow();

        assertEquals(List.of(1L, 2L, 3L, 4L), path.conceptIds());
        assertEquals(9, path.totalStrength());
    }

    @Test
    void unconnectedConceptsHaveNoPath() {
        assertTrue(finder.fewestSteps(1, 6).isEmpty());
        assertTrue(finder.strongest(1, 6).isEmpty());
    }

    @Test
    void unknownConceptsHaveNoPath() {
        assertTrue(finder.fewestSteps(1, 99).isEmpty());
        assertTrue(finder.strongest(99, 1).isEmpty());
    }

    @Test
    void aConceptReachesItselfInZeroSteps() {
        Path path = finder.fewestSteps(2, 2).orElseThrow();

        assertEquals(0, path.length());
        assertEquals(2L, path.end().id());
    }

    @Test
    void sentencesFollowTheStoredDirectionNotTheDirectionOfTravel() {
        Path path = finder.fewestSteps(2, 1).orElseThrow(); // travelling B -> A, but the link is A -> B
        Path.Step step = path.steps().get(0);

        assertFalse(step.forward());
        assertEquals("A leads to B.", step.sentence());
        assertEquals("B reaches A in 1 step.", path.summary());
    }

    @Test
    void alternativesAreOtherRoutesShortestFirst() {
        Path best = finder.fewestSteps(1, 4).orElseThrow();
        List<Path> others = finder.alternatives(1, 4, 5, 3, best);

        assertEquals(1, others.size());
        assertEquals(List.of(1L, 2L, 3L, 4L), others.get(0).conceptIds());
    }

    @Test
    void alternativesRespectTheStepLimitAndTheResultLimit() {
        Path best = finder.fewestSteps(1, 4).orElseThrow();

        assertTrue(finder.alternatives(1, 4, 2, 3, best).isEmpty(), "the 3-step route is longer than the limit");
        assertTrue(finder.alternatives(1, 4, 5, 0, best).isEmpty());
        assertEquals(2, finder.alternatives(1, 4, 5, 3, null).size(), "with nothing excluded both routes appear");
    }

    @Test
    void routesNeverRevisitAConcept() {
        // A square with a diagonal creates several routes, none of which may loop.
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "A").concept(2, "B").concept(3, "C").concept(4, "D")
                .link(1, 2, "x", 3).link(2, 3, "x", 3).link(3, 4, "x", 3).link(4, 1, "x", 3).link(1, 3, "x", 3).build();
        List<Path> routes = new PathFinder(graph).alternatives(1, 3, 6, 10, null);

        assertFalse(routes.isEmpty());
        for (Path route : routes) {
            assertEquals(route.conceptIds().size(), route.conceptIds().stream().distinct().count());
        }
    }

    @Test
    void parallelRelationsUseTheStrongestOne() {
        KnowledgeGraph graph = new GraphBuilder().concept(1, "A").concept(2, "B")
                .link(1, 2, "faint echo of", 1).link(1, 2, "is a form of", 5).build();
        Path path = new PathFinder(graph).fewestSteps(1, 2).orElseThrow();

        assertEquals("is a form of", path.steps().get(0).relation().label());
    }

    @Test
    void equalLengthRoutesPreferStrongerLinks() {
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "A").concept(2, "Weak middle").concept(3, "Strong middle").concept(4, "D")
                .link(1, 2, "x", 1).link(2, 4, "x", 1).link(1, 3, "x", 5).link(3, 4, "x", 5).build();
        Optional<Path> path = new PathFinder(graph).fewestSteps(1, 4);

        assertEquals(List.of(1L, 3L, 4L), path.orElseThrow().conceptIds());
    }
}
