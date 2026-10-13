package com.lelentandane.synthesis.graph;

import com.lelentandane.synthesis.model.Concept;
import com.lelentandane.synthesis.model.Relation;
import java.util.ArrayList;
import java.util.List;

/** Small helper that makes test graphs easy to read. */
final class GraphBuilder {

    private final List<Concept> concepts = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();
    private long nextRelationId = 1;

    GraphBuilder concept(long id, String name) {
        return concept(id, name, "General");
    }

    GraphBuilder concept(long id, String name, String discipline) {
        concepts.add(new Concept(id, name, discipline, ""));
        return this;
    }

    GraphBuilder link(long from, long to, String label, int strength) {
        relations.add(new Relation(nextRelationId++, from, to, label, strength));
        return this;
    }

    KnowledgeGraph build() {
        return new KnowledgeGraph(concepts, relations);
    }
}
