package com.lelentandane.synthesis.graph;

import com.lelentandane.synthesis.model.Concept;
import com.lelentandane.synthesis.model.Relation;
import java.util.ArrayList;
import java.util.List;

/** A route through the graph, from a start concept along a list of steps. */
public record Path(Concept start, List<Step> steps) {

    /** One hop. forward says whether the relation points the way we travelled. */
    public record Step(Concept from, Concept to, Relation relation, boolean forward) {

        /** A plain sentence that follows the relation's stored direction, not the direction of travel. */
        public String sentence() {
            Concept source = forward ? from : to;
            Concept target = forward ? to : from;
            return source.name() + " " + relation.label() + " " + target.name() + ".";
        }
    }

    public Path {
        steps = List.copyOf(steps);
    }

    public int length() {
        return steps.size();
    }

    public int totalStrength() {
        int total = 0;
        for (Step step : steps) {
            total += step.relation().strength();
        }
        return total;
    }

    public Concept end() {
        return steps.isEmpty() ? start : steps.get(steps.size() - 1).to();
    }

    public List<Concept> concepts() {
        List<Concept> result = new ArrayList<>();
        result.add(start);
        for (Step step : steps) {
            result.add(step.to());
        }
        return result;
    }

    public List<Long> conceptIds() {
        List<Long> ids = new ArrayList<>();
        for (Concept concept : concepts()) {
            ids.add(concept.id());
        }
        return ids;
    }

    /** One-line summary, for example: "Habit loop reaches Brand voice in 3 steps." */
    public String summary() {
        String noun = length() == 1 ? "step" : "steps";
        return start.name() + " reaches " + end().name() + " in " + length() + " " + noun + ".";
    }
}
