package com.lelentandane.synthesis.web;

import com.lelentandane.synthesis.graph.Insights;
import com.lelentandane.synthesis.graph.Path;
import com.lelentandane.synthesis.model.Concept;
import com.lelentandane.synthesis.model.Relation;
import com.lelentandane.synthesis.service.GraphService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns domain objects into plain maps and lists, ready to be written as JSON. */
final class ApiMapper {

    private ApiMapper() {
    }

    static Map<String, Object> concept(Concept concept) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", concept.id());
        map.put("name", concept.name());
        map.put("discipline", concept.discipline());
        map.put("description", concept.description());
        return map;
    }

    static Map<String, Object> relation(Relation relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", relation.id());
        map.put("fromId", relation.fromId());
        map.put("toId", relation.toId());
        map.put("label", relation.label());
        map.put("strength", relation.strength());
        return map;
    }

    static Map<String, Object> graph(GraphService.Snapshot snapshot) {
        List<Object> concepts = new ArrayList<>();
        snapshot.concepts().forEach(c -> concepts.add(concept(c)));
        List<Object> relations = new ArrayList<>();
        snapshot.relations().forEach(r -> relations.add(relation(r)));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("concepts", concepts);
        map.put("relations", relations);
        return map;
    }

    /** The export format refers to concepts by name, so a file does not depend on database ids. */
    static Map<String, Object> export(GraphService.Snapshot snapshot) {
        Map<Long, String> names = new LinkedHashMap<>();
        List<Object> concepts = new ArrayList<>();
        for (Concept c : snapshot.concepts()) {
            names.put(c.id(), c.name());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", c.name());
            item.put("discipline", c.discipline());
            item.put("description", c.description());
            concepts.add(item);
        }
        List<Object> relations = new ArrayList<>();
        for (Relation r : snapshot.relations()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("from", names.get(r.fromId()));
            item.put("to", names.get(r.toId()));
            item.put("label", r.label());
            item.put("strength", r.strength());
            relations.add(item);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("version", 1);
        map.put("concepts", concepts);
        map.put("relations", relations);
        return map;
    }

    static Map<String, Object> path(Path path) {
        List<Object> steps = new ArrayList<>();
        for (Path.Step step : path.steps()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fromId", step.from().id());
            item.put("toId", step.to().id());
            item.put("relationId", step.relation().id());
            item.put("label", step.relation().label());
            item.put("strength", step.relation().strength());
            item.put("forward", step.forward());
            item.put("sentence", step.sentence());
            steps.add(item);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("length", path.length());
        map.put("strength", path.totalStrength());
        map.put("summary", path.summary());
        map.put("conceptIds", path.conceptIds());
        map.put("steps", steps);
        return map;
    }

    static Map<String, Object> pathResult(GraphService.PathResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", result.mode());
        if (result.best().isEmpty()) {
            map.put("found", false);
            map.put("summary", result.from().name() + " and " + result.to().name()
                    + " are not connected yet. Add a link between their groups to create a route.");
            return map;
        }
        map.put("found", true);
        map.putAll(path(result.best().get()));
        List<Object> alternatives = new ArrayList<>();
        result.alternatives().forEach(p -> alternatives.add(path(p)));
        map.put("alternatives", alternatives);
        return map;
    }

    static Map<String, Object> insights(GraphService.InsightsResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("concepts", result.conceptCount());
        counts.put("relations", result.relationCount());
        counts.put("clusters", result.components().size());
        map.put("counts", counts);

        List<Object> keystones = new ArrayList<>();
        for (Insights.Keystone k : result.keystones()) {
            Map<String, Object> item = new LinkedHashMap<>(concept(k.concept()));
            item.remove("description");
            item.put("degree", k.degree());
            item.put("betweenness", k.betweenness());
            keystones.add(item);
        }
        map.put("keystones", keystones);

        List<Object> components = new ArrayList<>();
        for (Insights.Component c : result.components()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("size", c.size());
            item.put("conceptIds", c.conceptIds());
            item.put("disciplines", c.disciplines());
            List<String> sample = new ArrayList<>();
            for (long id : c.conceptIds()) {
                if (sample.size() < 4) {
                    sample.add(result.graph().require(id).name());
                }
            }
            item.put("sample", sample);
            components.add(item);
        }
        map.put("clusters", components);

        List<Object> links = new ArrayList<>();
        for (Insights.DisciplineLink l : result.disciplineLinks()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("first", l.first());
            item.put("second", l.second());
            item.put("count", l.count());
            links.add(item);
        }
        map.put("disciplineLinks", links);

        List<Object> suggestions = new ArrayList<>();
        for (Insights.Suggestion s : result.suggestions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("firstId", s.first().id());
            item.put("firstName", s.first().name());
            item.put("secondId", s.second().id());
            item.put("secondName", s.second().name());
            List<String> shared = new ArrayList<>();
            s.shared().forEach(c -> shared.add(c.name()));
            item.put("shared", shared);
            item.put("score", s.score());
            suggestions.add(item);
        }
        map.put("suggestions", suggestions);

        List<Object> orphans = new ArrayList<>();
        result.orphans().forEach(c -> orphans.add(concept(c)));
        map.put("orphans", orphans);
        return map;
    }
}
