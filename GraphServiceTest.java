package com.lelentandane.synthesis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lelentandane.synthesis.db.Database;
import com.lelentandane.synthesis.model.Concept;
import com.lelentandane.synthesis.model.Relation;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphServiceTest {

    private Database database;
    private GraphService service;

    @BeforeEach
    void setUp() {
        database = Database.inMemory();
        service = new GraphService(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static int statusOf(Runnable action) {
        return assertThrows(ApiException.class, action::run).status();
    }

    // ---------------------------------------------------------------- concepts

    @Test
    void conceptNamesAreTrimmedAndWhitespaceIsCollapsed() {
        Concept concept = service.createConcept("  Feedback    loop ", " Biology ", "  A circle.  ");

        assertEquals("Feedback loop", concept.name());
        assertEquals("Biology", concept.discipline());
        assertEquals("A circle.", concept.description());
    }

    @Test
    void duplicateNamesAreRejectedIgnoringCase() {
        service.createConcept("Habit loop", "Psychology", "");

        assertEquals(409, statusOf(() -> service.createConcept("habit LOOP", "Psychology", "")));
    }

    @Test
    void disciplinesReuseTheExistingSpelling() {
        service.createConcept("A", "Biology", "");
        Concept second = service.createConcept("B", "biology", "");

        assertEquals("Biology", second.discipline());
    }

    @Test
    void invalidConceptsAreRejectedWithClearStatuses() {
        assertEquals(400, statusOf(() -> service.createConcept("   ", "Biology", "")));
        assertEquals(400, statusOf(() -> service.createConcept(null, "Biology", "")));
        assertEquals(400, statusOf(() -> service.createConcept("A", "", "")));
        assertEquals(400, statusOf(() -> service.createConcept("x".repeat(81), "Biology", "")));
        assertEquals(400, statusOf(() -> service.createConcept("A", "y".repeat(41), "")));
        assertEquals(400, statusOf(() -> service.createConcept("A", "Biology", "z".repeat(501))));
    }

    @Test
    void conceptsCanBeEditedButNotRenamedToAnExistingName() {
        Concept a = service.createConcept("A", "Biology", "");
        service.createConcept("B", "Biology", "");

        Concept renamed = service.updateConcept(a.id(), "A2", "Writing", "new text");
        assertEquals("A2", renamed.name());
        assertEquals("Writing", renamed.discipline());

        assertEquals(409, statusOf(() -> service.updateConcept(a.id(), "b", "Biology", "")));
        assertEquals(404, statusOf(() -> service.updateConcept(999, "Z", "Biology", "")));
        // keeping your own name (even with different casing) is not a conflict
        assertEquals("a2", service.updateConcept(a.id(), "a2", "Writing", "again").name());
    }

    @Test
    void deletingAConceptDeletesItsRelations() {
        Concept a = service.createConcept("A", "Biology", "");
        Concept b = service.createConcept("B", "Biology", "");
        service.createRelation(a.id(), b.id(), "feeds", 3);

        service.deleteConcept(a.id());

        assertTrue(service.snapshot().relations().isEmpty());
        assertEquals(404, statusOf(() -> service.deleteConcept(a.id())));
    }

    // ---------------------------------------------------------------- relations

    @Test
    void relationsAreValidated() {
        Concept a = service.createConcept("A", "Biology", "");
        Concept b = service.createConcept("B", "Biology", "");

        assertEquals(400, statusOf(() -> service.createRelation(a.id(), a.id(), "x", 3)));
        assertEquals(404, statusOf(() -> service.createRelation(a.id(), 999, "x", 3)));
        assertEquals(400, statusOf(() -> service.createRelation(a.id(), b.id(), "  ", 3)));
        assertEquals(400, statusOf(() -> service.createRelation(a.id(), b.id(), "x".repeat(61), 3)));
        assertEquals(400, statusOf(() -> service.createRelation(a.id(), b.id(), "x", 0)));
        assertEquals(400, statusOf(() -> service.createRelation(a.id(), b.id(), "x", 6)));
    }

    @Test
    void theSameLinkCannotBeAddedTwiceButDifferentLabelsCan() {
        Concept a = service.createConcept("A", "Biology", "");
        Concept b = service.createConcept("B", "Biology", "");
        service.createRelation(a.id(), b.id(), "Feeds", 3);

        assertEquals(409, statusOf(() -> service.createRelation(a.id(), b.id(), "feeds", 4)));
        Relation other = service.createRelation(a.id(), b.id(), "mirrors", 2);
        assertEquals(2, service.snapshot().relations().size());

        service.deleteRelation(other.id());
        assertEquals(1, service.snapshot().relations().size());
        assertEquals(404, statusOf(() -> service.deleteRelation(other.id())));
    }

    // ---------------------------------------------------------------- queries

    @Test
    void pathsAreFoundInBothModes() {
        Concept a = service.createConcept("A", "Biology", "");
        Concept b = service.createConcept("B", "Psychology", "");
        Concept c = service.createConcept("C", "Writing", "");
        service.createRelation(a.id(), b.id(), "feeds", 4);
        service.createRelation(b.id(), c.id(), "inspires", 4);

        GraphService.PathResult fewest = service.findPaths(a.id(), c.id(), "fewest");
        GraphService.PathResult strongest = service.findPaths(a.id(), c.id(), "strongest");

        assertEquals(2, fewest.best().orElseThrow().length());
        assertEquals(2, strongest.best().orElseThrow().length());
        assertTrue(fewest.alternatives().isEmpty());
    }

    @Test
    void pathQueriesAreValidated() {
        Concept a = service.createConcept("A", "Biology", "");
        Concept b = service.createConcept("B", "Biology", "");

        assertEquals(400, statusOf(() -> service.findPaths(a.id(), b.id(), "fastest")));
        assertEquals(400, statusOf(() -> service.findPaths(a.id(), a.id(), "fewest")));
        assertEquals(404, statusOf(() -> service.findPaths(a.id(), 999, "fewest")));
        assertTrue(service.findPaths(a.id(), b.id(), "fewest").best().isEmpty(), "unconnected concepts give an empty result");
    }

    // ---------------------------------------------------------------- demo, import, export

    @Test
    void theDemoGraphLoadsOnlyIntoAnEmptyGraph() {
        GraphService.Snapshot snapshot = service.loadDemo();

        assertEquals(DemoData.concepts().size(), snapshot.concepts().size());
        assertEquals(DemoData.relations().size(), snapshot.relations().size());
        assertEquals(409, assertThrows(ApiException.class, service::loadDemo).status());
    }

    @Test
    void theDemoGraphIsOneConnectedClusterSpanningSixDisciplines() {
        service.loadDemo();
        GraphService.InsightsResult insights = service.insights();

        assertEquals(1, insights.components().size());
        assertEquals(6, insights.components().get(0).disciplines().size());
        assertTrue(insights.orphans().isEmpty());
        assertFalse(insights.keystones().isEmpty());
        assertFalse(insights.suggestions().isEmpty());
        assertFalse(insights.disciplineLinks().isEmpty());
    }

    @Test
    void anyTwoDemoConceptsCanBeConnected() {
        GraphService.Snapshot snapshot = service.loadDemo();
        long retrograde = idOf(snapshot, "Mercury retrograde");
        long voice = idOf(snapshot, "Brand voice");

        GraphService.PathResult result = service.findPaths(retrograde, voice, "fewest");

        assertTrue(result.best().isPresent());
        assertTrue(result.best().get().length() >= 2);
    }

    private static long idOf(GraphService.Snapshot snapshot, String name) {
        return snapshot.concepts().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
    }

    @Test
    void importReplacesTheGraphAndResolvesLinksByName() {
        service.createConcept("Old", "Old field", "");

        GraphService.Snapshot snapshot = service.replaceAll(
                List.of(new GraphService.ConceptDraft("A", "Biology", ""), new GraphService.ConceptDraft("B", "biology", "text")),
                List.of(new GraphService.RelationDraft("a", "B", "feeds", 4)));

        assertEquals(2, snapshot.concepts().size());
        assertEquals(1, snapshot.relations().size());
        assertEquals("Biology", snapshot.concepts().get(1).discipline());
    }

    @Test
    void aBadImportChangesNothing() {
        service.createConcept("Keep me", "Biology", "");

        List<GraphService.ConceptDraft> concepts = List.of(new GraphService.ConceptDraft("A", "Biology", ""));
        assertEquals(400, statusOf(() -> service.replaceAll(concepts,
                List.of(new GraphService.RelationDraft("A", "Missing", "x", 3)))));
        assertEquals(400, statusOf(() -> service.replaceAll(
                List.of(new GraphService.ConceptDraft("A", "Biology", ""), new GraphService.ConceptDraft("a", "Biology", "")),
                List.of())));
        assertEquals(400, statusOf(() -> service.replaceAll(concepts,
                List.of(new GraphService.RelationDraft("A", "A", "x", 3)))));
        assertEquals(400, statusOf(() -> service.replaceAll(
                List.of(new GraphService.ConceptDraft("", "Biology", "")), List.of())));

        List<Concept> remaining = service.snapshot().concepts();
        assertEquals(1, remaining.size());
        assertEquals("Keep me", remaining.get(0).name());
    }

    @Test
    void duplicateLinksInAnImportAreMergedInsteadOfFailing() {
        GraphService.Snapshot snapshot = service.replaceAll(
                List.of(new GraphService.ConceptDraft("A", "X", ""), new GraphService.ConceptDraft("B", "X", "")),
                List.of(new GraphService.RelationDraft("A", "B", "feeds", 3), new GraphService.RelationDraft("A", "B", "FEEDS", 5)));

        assertEquals(1, snapshot.relations().size());
    }
}
