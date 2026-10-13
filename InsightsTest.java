package com.lelentandane.synthesis.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InsightsTest {

    @Test
    void betweennessIsHighestForConceptsOnTheRouteBetweenOthers() {
        // A - B - C - D: B and C each sit between two pairs of concepts.
        KnowledgeGraph graph = new GraphBuilder().concept(1, "A").concept(2, "B").concept(3, "C").concept(4, "D")
                .link(1, 2, "x", 3).link(2, 3, "x", 3).link(3, 4, "x", 3).build();
        Map<Long, Double> scores = new Insights(graph).betweenness();

        assertEquals(0.0, scores.get(1L), 1e-9);
        assertEquals(2.0, scores.get(2L), 1e-9);
        assertEquals(2.0, scores.get(3L), 1e-9);
        assertEquals(0.0, scores.get(4L), 1e-9);
    }

    @Test
    void theCentreOfAStarCarriesEveryRoute() {
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "Hub").concept(2, "L1").concept(3, "L2").concept(4, "L3").concept(5, "L4")
                .link(1, 2, "x", 3).link(1, 3, "x", 3).link(1, 4, "x", 3).link(1, 5, "x", 3).build();
        Insights insights = new Insights(graph);

        assertEquals(6.0, insights.betweenness().get(1L), 1e-9); // C(4,2) pairs of leaves
        List<Insights.Keystone> keystones = insights.keystones(5);
        assertEquals(1, keystones.size(), "leaves score zero and are left out");
        assertEquals("Hub", keystones.get(0).concept().name());
        assertEquals(4, keystones.get(0).degree());
    }

    @Test
    void sharedShortestRoutesSplitTheCredit() {
        // A connects to D through either B or C, so each earns half of that pair.
        KnowledgeGraph graph = new GraphBuilder().concept(1, "A").concept(2, "B").concept(3, "C").concept(4, "D")
                .link(1, 2, "x", 3).link(1, 3, "x", 3).link(2, 4, "x", 3).link(3, 4, "x", 3).build();
        Map<Long, Double> scores = new Insights(graph).betweenness();

        assertEquals(0.5, scores.get(2L), 1e-9);
        assertEquals(0.5, scores.get(3L), 1e-9);
    }

    @Test
    void componentsAreSeparateClustersLargestFirst() {
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "A", "Biology").concept(2, "B", "Psychology").concept(3, "C", "Biology")
                .concept(4, "D").concept(5, "E")
                .link(1, 2, "x", 3).link(2, 3, "x", 3).link(4, 5, "x", 3).build();
        List<Insights.Component> components = new Insights(graph).components();

        assertEquals(2, components.size());
        assertEquals(3, components.get(0).size());
        assertEquals(List.of("Biology", "Psychology"), components.get(0).disciplines());
        assertEquals(2, components.get(1).size());
    }

    @Test
    void orphansHaveNoRelations() {
        KnowledgeGraph graph = new GraphBuilder().concept(1, "A").concept(2, "B").concept(3, "Lonely")
                .link(1, 2, "x", 3).build();
        List<String> names = new Insights(graph).orphans().stream().map(c -> c.name()).toList();

        assertEquals(List.of("Lonely"), names);
    }

    @Test
    void disciplineLinksCountRelationsThatCrossBoundaries() {
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "A", "Biology").concept(2, "B", "Biology").concept(3, "C", "Writing").concept(4, "D", "Writing")
                .link(1, 2, "x", 3)   // same discipline: not counted
                .link(1, 3, "x", 3).link(2, 4, "x", 3).link(4, 1, "x", 3).build();
        List<Insights.DisciplineLink> links = new Insights(graph).disciplineLinks();

        assertEquals(1, links.size());
        assertEquals("Biology", links.get(0).first());
        assertEquals("Writing", links.get(0).second());
        assertEquals(3, links.get(0).count());
    }

    @Test
    void suggestionsPairUnlinkedConceptsThatShareNeighbours() {
        // X and Y are both linked to M and N but not to each other.
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "X").concept(2, "Y").concept(3, "M").concept(4, "N")
                .link(1, 3, "x", 3).link(1, 4, "x", 3).link(2, 3, "x", 3).link(2, 4, "x", 3).build();
        List<Insights.Suggestion> suggestions = new Insights(graph).suggestions(10);

        // Both pairs qualify: X and Y share M and N, and M and N share X and Y.
        assertEquals(2, suggestions.size());
        Insights.Suggestion xy = suggestions.stream()
                .filter(s -> s.first().name().equals("X") && s.second().name().equals("Y")).findFirst().orElseThrow();
        assertEquals(List.of("M", "N"), xy.shared().stream().map(c -> c.name()).toList());
        assertEquals(1.0, xy.score(), 1e-9);
        assertTrue(suggestions.stream().anyMatch(s -> s.first().name().equals("M") && s.second().name().equals("N")));
    }

    @Test
    void suggestionsSkipLinkedPairsAndPairsWithOnlyOneSharedNeighbour() {
        KnowledgeGraph graph = new GraphBuilder()
                .concept(1, "X").concept(2, "Y").concept(3, "M")
                .link(1, 3, "x", 3).link(2, 3, "x", 3).build();

        assertTrue(new Insights(graph).suggestions(10).isEmpty(), "one shared neighbour is not enough");

        KnowledgeGraph linked = new GraphBuilder()
                .concept(1, "X").concept(2, "Y").concept(3, "M").concept(4, "N")
                .link(1, 3, "x", 3).link(1, 4, "x", 3).link(2, 3, "x", 3).link(2, 4, "x", 3).link(1, 2, "x", 3).build();
        assertTrue(new Insights(linked).suggestions(10).stream()
                .noneMatch(s -> s.first().name().equals("X") && s.second().name().equals("Y")));
    }

    @Test
    void suggestionsRespectTheLimit() {
        GraphBuilder builder = new GraphBuilder().concept(1, "M").concept(2, "N");
        for (long id = 3; id <= 8; id++) {
            builder.concept(id, "C" + id).link(id, 1, "x", 3).link(id, 2, "x", 3);
        }
        assertEquals(3, new Insights(builder.build()).suggestions(3).size());
    }
}
