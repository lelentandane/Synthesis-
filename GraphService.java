package com.lelentandane.synthesis.service;

import com.lelentandane.synthesis.db.ConceptRepository;
import com.lelentandane.synthesis.db.Database;
import com.lelentandane.synthesis.db.RelationRepository;
import com.lelentandane.synthesis.graph.Insights;
import com.lelentandane.synthesis.graph.KnowledgeGraph;
import com.lelentandane.synthesis.graph.Path;
import com.lelentandane.synthesis.graph.PathFinder;
import com.lelentandane.synthesis.model.Concept;
import com.lelentandane.synthesis.model.Relation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The application's rules: validation, orchestration and graph queries.
 * Methods are synchronized because the web server handles requests on several threads
 * while the database uses a single connection.
 */
public final class GraphService {

    public static final int MAX_NAME = 80;
    public static final int MAX_DISCIPLINE = 40;
    public static final int MAX_DESCRIPTION = 500;
    public static final int MAX_LABEL = 60;
    public static final int MAX_IMPORT_CONCEPTS = 2000;

    public record Snapshot(List<Concept> concepts, List<Relation> relations) {
    }

    public record PathResult(Concept from, Concept to, String mode, Optional<Path> best, List<Path> alternatives) {
    }

    public record InsightsResult(int conceptCount, int relationCount, List<Insights.Component> components,
                                 List<Insights.Keystone> keystones, List<Insights.DisciplineLink> disciplineLinks,
                                 List<Insights.Suggestion> suggestions, List<Concept> orphans, KnowledgeGraph graph) {
    }

    /** A concept as written in an import file. Relations refer to concepts by name. */
    public record ConceptDraft(String name, String discipline, String description) {
    }

    public record RelationDraft(String from, String to, String label, int strength) {
    }

    private final Database database;
    private final ConceptRepository concepts;
    private final RelationRepository relations;

    public GraphService(Database database) {
        this.database = database;
        this.concepts = new ConceptRepository(database);
        this.relations = new RelationRepository(database);
    }

    // ------------------------------------------------------------------ reading

    public synchronized Snapshot snapshot() {
        return new Snapshot(concepts.findAll(), relations.findAll());
    }

    private KnowledgeGraph graph() {
        return new KnowledgeGraph(concepts.findAll(), relations.findAll());
    }

    // ------------------------------------------------------------------ concepts

    public synchronized Concept createConcept(String name, String discipline, String description) {
        String cleanName = cleanName(name);
        String cleanDiscipline = canonicalDiscipline(cleanDiscipline(discipline));
        String cleanDescription = cleanDescription(description);
        if (concepts.findByName(cleanName).isPresent()) {
            throw ApiException.conflict("A concept named \"" + cleanName + "\" already exists.");
        }
        long id = concepts.insert(cleanName, cleanDiscipline, cleanDescription);
        return new Concept(id, cleanName, cleanDiscipline, cleanDescription);
    }

    public synchronized Concept updateConcept(long id, String name, String discipline, String description) {
        if (concepts.findById(id).isEmpty()) {
            throw ApiException.notFound("That concept no longer exists.");
        }
        String cleanName = cleanName(name);
        String cleanDiscipline = canonicalDiscipline(cleanDiscipline(discipline));
        String cleanDescription = cleanDescription(description);
        Optional<Concept> sameName = concepts.findByName(cleanName);
        if (sameName.isPresent() && sameName.get().id() != id) {
            throw ApiException.conflict("A concept named \"" + cleanName + "\" already exists.");
        }
        concepts.update(id, cleanName, cleanDiscipline, cleanDescription);
        return new Concept(id, cleanName, cleanDiscipline, cleanDescription);
    }

    public synchronized void deleteConcept(long id) {
        if (!concepts.delete(id)) {
            throw ApiException.notFound("That concept no longer exists.");
        }
    }

    // ------------------------------------------------------------------ relations

    public synchronized Relation createRelation(long fromId, long toId, String label, int strength) {
        if (fromId == toId) {
            throw ApiException.badRequest("A concept cannot be linked to itself.");
        }
        if (concepts.findById(fromId).isEmpty() || concepts.findById(toId).isEmpty()) {
            throw ApiException.notFound("Both concepts must exist before they can be linked.");
        }
        String cleanLabel = cleanLabel(label);
        checkStrength(strength);
        if (relations.exists(fromId, toId, cleanLabel)) {
            throw ApiException.conflict("These two concepts already have that link.");
        }
        long id = relations.insert(fromId, toId, cleanLabel, strength);
        return new Relation(id, fromId, toId, cleanLabel, strength);
    }

    public synchronized void deleteRelation(long id) {
        if (!relations.delete(id)) {
            throw ApiException.notFound("That link no longer exists.");
        }
    }

    // ------------------------------------------------------------------ queries

    /** mode is "fewest" (fewest steps) or "strongest" (strongest links). */
    public synchronized PathResult findPaths(long fromId, long toId, String mode) {
        if (!"fewest".equals(mode) && !"strongest".equals(mode)) {
            throw ApiException.badRequest("mode must be \"fewest\" or \"strongest\".");
        }
        if (fromId == toId) {
            throw ApiException.badRequest("Choose two different concepts.");
        }
        KnowledgeGraph graph = graph();
        Concept from = graph.concept(fromId).orElseThrow(() -> ApiException.notFound("The first concept does not exist."));
        Concept to = graph.concept(toId).orElseThrow(() -> ApiException.notFound("The second concept does not exist."));

        PathFinder finder = new PathFinder(graph);
        Optional<Path> best = "fewest".equals(mode) ? finder.fewestSteps(fromId, toId) : finder.strongest(fromId, toId);
        List<Path> others = best.isEmpty() ? List.of()
                : finder.alternatives(fromId, toId, Math.min(best.get().length() + 2, 6), 3, best.get());
        return new PathResult(from, to, mode, best, others);
    }

    public synchronized InsightsResult insights() {
        KnowledgeGraph graph = graph();
        Insights insights = new Insights(graph);
        return new InsightsResult(graph.conceptCount(), graph.relationCount(), insights.components(),
                insights.keystones(5), insights.disciplineLinks(), insights.suggestions(8), insights.orphans(), graph);
    }

    // ------------------------------------------------------------------ demo, import, export

    public synchronized Snapshot loadDemo() {
        if (concepts.count() > 0) {
            throw ApiException.conflict("The demo graph can only be loaded into an empty graph.");
        }
        List<ConceptDraft> drafts = new ArrayList<>();
        for (DemoData.ConceptSeed seed : DemoData.concepts()) {
            drafts.add(new ConceptDraft(seed.name(), seed.discipline(), seed.description()));
        }
        List<RelationDraft> links = new ArrayList<>();
        for (DemoData.RelationSeed seed : DemoData.relations()) {
            links.add(new RelationDraft(seed.from(), seed.to(), seed.label(), seed.strength()));
        }
        replaceAll(drafts, links);
        return new Snapshot(concepts.findAll(), relations.findAll());
    }

    /**
     * Replaces the whole graph with the given data. Everything is validated first and written in one
     * transaction, so a bad file can never leave the graph half-imported.
     */
    public synchronized Snapshot replaceAll(List<ConceptDraft> conceptDrafts, List<RelationDraft> relationDrafts) {
        if (conceptDrafts.size() > MAX_IMPORT_CONCEPTS) {
            throw ApiException.badRequest("A graph can have at most " + MAX_IMPORT_CONCEPTS + " concepts.");
        }
        Map<String, ConceptDraft> byName = new HashMap<>();
        List<ConceptDraft> cleanConcepts = new ArrayList<>();
        Map<String, String> disciplineSpelling = new HashMap<>();
        for (ConceptDraft draft : conceptDrafts) {
            String name = cleanName(draft.name());
            String discipline = cleanDiscipline(draft.discipline());
            discipline = disciplineSpelling.computeIfAbsent(discipline.toLowerCase(Locale.ROOT), key -> clean(draft.discipline()));
            ConceptDraft clean = new ConceptDraft(name, cleanDiscipline(discipline), cleanDescription(draft.description()));
            if (byName.put(name.toLowerCase(Locale.ROOT), clean) != null) {
                throw ApiException.badRequest("The concept \"" + name + "\" appears more than once.");
            }
            cleanConcepts.add(clean);
        }
        List<RelationDraft> cleanRelations = new ArrayList<>();
        for (RelationDraft draft : relationDrafts) {
            ConceptDraft from = byName.get(clean(draft.from()).toLowerCase(Locale.ROOT));
            ConceptDraft to = byName.get(clean(draft.to()).toLowerCase(Locale.ROOT));
            if (from == null || to == null) {
                throw ApiException.badRequest("A link refers to a concept that is not in the file: \""
                        + draft.from() + "\" to \"" + draft.to() + "\".");
            }
            if (from == to) {
                throw ApiException.badRequest("A concept cannot be linked to itself: \"" + from.name() + "\".");
            }
            checkStrength(draft.strength());
            cleanRelations.add(new RelationDraft(from.name(), to.name(), cleanLabel(draft.label()), draft.strength()));
        }

        database.transaction(connection -> {
            concepts.deleteAll();
            Map<String, Long> ids = new HashMap<>();
            for (ConceptDraft concept : cleanConcepts) {
                ids.put(concept.name().toLowerCase(Locale.ROOT),
                        concepts.insert(concept.name(), concept.discipline(), concept.description()));
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (RelationDraft relation : cleanRelations) {
                long fromId = ids.get(relation.from().toLowerCase(Locale.ROOT));
                long toId = ids.get(relation.to().toLowerCase(Locale.ROOT));
                String key = fromId + ">" + toId + ">" + relation.label().toLowerCase(Locale.ROOT);
                if (seen.add(key)) {
                    relations.insert(fromId, toId, relation.label(), relation.strength());
                }
            }
            return null;
        });
        return new Snapshot(concepts.findAll(), relations.findAll());
    }

    // ------------------------------------------------------------------ validation

    private static String clean(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static String cleanName(String name) {
        String value = clean(name);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Give the concept a name.");
        }
        if (value.length() > MAX_NAME) {
            throw ApiException.badRequest("A concept name can be at most " + MAX_NAME + " characters.");
        }
        return value;
    }

    private static String cleanDiscipline(String discipline) {
        String value = clean(discipline);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Choose or type a discipline.");
        }
        if (value.length() > MAX_DISCIPLINE) {
            throw ApiException.badRequest("A discipline can be at most " + MAX_DISCIPLINE + " characters.");
        }
        return value;
    }

    /** Reuses the spelling of an existing discipline, so "biology" and "Biology" stay one group. */
    private String canonicalDiscipline(String discipline) {
        for (Concept existing : concepts.findAll()) {
            if (existing.discipline().equalsIgnoreCase(discipline)) {
                return existing.discipline();
            }
        }
        return discipline;
    }

    private static String cleanDescription(String description) {
        String value = description == null ? "" : description.trim();
        if (value.length() > MAX_DESCRIPTION) {
            throw ApiException.badRequest("A description can be at most " + MAX_DESCRIPTION + " characters.");
        }
        return value;
    }

    private static String cleanLabel(String label) {
        String value = clean(label);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Describe the link, for example \"influences\".");
        }
        if (value.length() > MAX_LABEL) {
            throw ApiException.badRequest("A link description can be at most " + MAX_LABEL + " characters.");
        }
        return value;
    }

    private static void checkStrength(int strength) {
        if (strength < 1 || strength > 5) {
            throw ApiException.badRequest("Strength must be a whole number from 1 to 5.");
        }
    }
}
