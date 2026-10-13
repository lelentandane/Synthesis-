package com.lelentandane.synthesis.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeGraphTest {

    @Test
    void relationsAreSearchableFromBothEnds() {
        KnowledgeGraph graph = new GraphBuilder().concept(1, "A").concept(2, "B").link(1, 2, "leads to", 3).build();

        assertEquals(1, graph.degree(1));
        assertEquals(1, graph.degree(2));
        assertTrue(graph.edges(1).get(0).forward());
        assertFalse(graph.edges(2).get(0).forward());
        assertEquals(Set.of(2L), graph.neighborIds(1));
        assertEquals(Set.of(1L), graph.neighborIds(2));
    }

    @Test
    void edgesAreSortedStrongestFirst() {
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "A").concept(2, "B").concept(3, "C").concept(4, "D")
                .link(1, 2, "weak", 1).link(1, 3, "strong", 5).link(1, 4, "medium", 3).build();

        List<Long> order = graph.edges(1).stream().map(KnowledgeGraph.Edge::neighborId).toList();
        assertEquals(List.of(3L, 4L, 2L), order);
    }

    @Test
    void parallelRelationsKeepOnlyTheStrongestAsTheBestEdge() {
        KnowledgeGraph graph = new GraphBuilder().concept(1, "A").concept(2, "B")
                .link(1, 2, "loosely related to", 2).link(1, 2, "is the same idea as", 5).build();

        assertEquals(2, graph.degree(1));
        assertEquals(1, graph.bestEdges(1).size());
        assertEquals("is the same idea as", graph.bestEdges(1).get(0).relation().label());
        assertEquals(1, graph.neighborIds(1).size());
    }

    @Test
    void relationsPointingAtMissingConceptsAreIgnored() {
        KnowledgeGraph graph = new GraphBuilder().concept(1, "A").link(1, 99, "ghost", 3).build();

        assertEquals(0, graph.degree(1));
        assertEquals(0, graph.relationCount());
    }

    @Test
    void requireThrowsForUnknownIds() {
        KnowledgeGraph graph = new GraphBuilder().concept(1, "A").build();

        assertThrows(NoSuchElementException.class, () -> graph.require(2));
        assertTrue(graph.concept(2).isEmpty());
        assertTrue(graph.contains(1));
    }
}
