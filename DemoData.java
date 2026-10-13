package com.lelentandane.synthesis.service;

import java.util.List;

/**
 * A starter graph that mixes disciplines, so the app has something interesting to explore
 * straight away. Relations are written as (from, label, to, strength).
 */
public final class DemoData {

    public record ConceptSeed(String name, String discipline, String description) {
    }

    public record RelationSeed(String from, String label, String to, int strength) {
    }

    private DemoData() {
    }

    public static List<ConceptSeed> concepts() {
        return List.of(
            // Biology
            new ConceptSeed("Homeostasis", "Biology", "The way a living system keeps its internal conditions stable."),
            new ConceptSeed("Feedback loop", "Biology", "A process whose output circles back to influence its own input."),
            new ConceptSeed("Natural selection", "Biology", "Traits that help organisms survive and reproduce become more common over time."),
            new ConceptSeed("Adaptation", "Biology", "A trait shaped by selection that suits an organism to its environment."),
            new ConceptSeed("Circadian rhythm", "Biology", "A roughly 24-hour internal cycle that regulates sleep, hormones and energy."),
            new ConceptSeed("Mutualism", "Biology", "A relationship where both species benefit."),
            new ConceptSeed("Ecosystem", "Biology", "A community of living things and their surroundings, linked by flows of energy and matter."),
            // Psychology
            new ConceptSeed("Cognitive dissonance", "Psychology", "The discomfort of holding beliefs or acting in ways that conflict."),
            new ConceptSeed("Self-concept", "Psychology", "The set of beliefs a person holds about who they are."),
            new ConceptSeed("Confirmation bias", "Psychology", "The tendency to notice and remember information that fits what we already believe."),
            new ConceptSeed("Habit loop", "Psychology", "A cue, a routine and a reward that repeat until the behaviour feels automatic."),
            new ConceptSeed("Self-affirmation theory", "Psychology", "People are motivated to protect their sense of integrity, and reflecting on core values helps."),
            new ConceptSeed("Narrative identity", "Psychology", "The evolving life story a person tells to make sense of who they are."),
            new ConceptSeed("Attachment style", "Psychology", "A pattern in how a person bonds with and relies on others."),
            new ConceptSeed("Growth mindset", "Psychology", "The belief that abilities can develop through effort and learning."),
            new ConceptSeed("Reflection", "Psychology", "Deliberately looking back on experience to understand it."),
            // Business
            new ConceptSeed("Brand positioning", "Business", "The place a brand aims to hold in the minds of its audience."),
            new ConceptSeed("Customer segmentation", "Business", "Dividing an audience into groups with shared needs."),
            new ConceptSeed("Value proposition", "Business", "The specific benefit a product promises to a specific audience."),
            new ConceptSeed("Network effect", "Business", "A product becomes more valuable as more people use it."),
            new ConceptSeed("Compounding", "Business", "Gains that earn further gains, so growth accelerates over time."),
            new ConceptSeed("Brand voice", "Business", "The consistent personality a brand shows in its words."),
            new ConceptSeed("Goal setting", "Business", "Choosing specific targets and the steps toward them."),
            // Writing
            new ConceptSeed("Storytelling", "Writing", "Shaping events into a narrative that holds attention and carries meaning."),
            new ConceptSeed("Narrative arc", "Writing", "The shape of a story: setup, rising tension, turning point, resolution."),
            new ConceptSeed("Unreliable narrator", "Writing", "A narrator whose account the reader has reason to doubt."),
            new ConceptSeed("Voice", "Writing", "The distinct sound of a writer on the page."),
            new ConceptSeed("Metaphor", "Writing", "Describing one thing in terms of another to reveal a likeness."),
            new ConceptSeed("Archetype", "Writing", "A recurring character or pattern that appears across stories and cultures."),
            new ConceptSeed("Character motivation", "Writing", "What a character wants, and why they act as they do."),
            // Astrology
            new ConceptSeed("Birth chart", "Astrology", "A map of the sky at the moment of birth, read as a symbolic portrait."),
            new ConceptSeed("Transit", "Astrology", "A planet's current position, read against the birth chart."),
            new ConceptSeed("Saturn return", "Astrology", "The transit when Saturn comes back to its birth position, around age 29 and 58."),
            new ConceptSeed("Mercury retrograde", "Astrology", "A period when Mercury appears to move backward, often linked with review and revision."),
            new ConceptSeed("Lunar cycle", "Astrology", "The Moon's monthly passage through its phases."),
            new ConceptSeed("Astrological symbolism", "Astrology", "The shared language of planets, signs and houses."),
            // Manifestation
            new ConceptSeed("Intention setting", "Manifestation", "Naming clearly what you are calling in."),
            new ConceptSeed("Scripting", "Manifestation", "Writing as if the desired reality is already happening."),
            new ConceptSeed("Visualization", "Manifestation", "Picturing an experience in vivid detail."),
            new ConceptSeed("Affirmation", "Manifestation", "A present-tense statement repeated to reinforce a belief."),
            new ConceptSeed("Identity shift", "Manifestation", "Becoming the person for whom the desired reality is natural.")
        );
    }

    public static List<RelationSeed> relations() {
        return List.of(
            new RelationSeed("Homeostasis", "is maintained by", "Feedback loop", 5),
            new RelationSeed("Circadian rhythm", "relies on", "Feedback loop", 4),
            new RelationSeed("Ecosystem", "relies on", "Feedback loop", 4),
            new RelationSeed("Mutualism", "shapes", "Ecosystem", 4),
            new RelationSeed("Mutualism", "resembles", "Network effect", 2),
            new RelationSeed("Natural selection", "produces", "Adaptation", 5),
            new RelationSeed("Adaptation", "supports", "Homeostasis", 2),
            new RelationSeed("Network effect", "is a form of", "Feedback loop", 4),
            new RelationSeed("Feedback loop", "drives", "Compounding", 3),
            new RelationSeed("Habit loop", "is an example of", "Feedback loop", 4),
            new RelationSeed("Habit loop", "compounds through", "Compounding", 3),
            new RelationSeed("Goal setting", "shapes", "Habit loop", 4),
            new RelationSeed("Growth mindset", "supports", "Goal setting", 3),
            new RelationSeed("Growth mindset", "shapes", "Self-concept", 3),
            new RelationSeed("Cognitive dissonance", "threatens", "Self-concept", 4),
            new RelationSeed("Self-affirmation theory", "protects", "Self-concept", 5),
            new RelationSeed("Confirmation bias", "filters evidence for", "Self-concept", 3),
            new RelationSeed("Attachment style", "shapes", "Self-concept", 3),
            new RelationSeed("Habit loop", "reinforces", "Narrative identity", 3),
            new RelationSeed("Narrative identity", "is part of", "Self-concept", 4),
            new RelationSeed("Narrative identity", "is expressed through", "Storytelling", 4),
            new RelationSeed("Reflection", "deepens", "Narrative identity", 4),
            new RelationSeed("Customer segmentation", "informs", "Value proposition", 5),
            new RelationSeed("Value proposition", "defines", "Brand positioning", 5),
            new RelationSeed("Brand positioning", "is communicated by", "Brand voice", 5),
            new RelationSeed("Brand positioning", "parallels", "Self-concept", 3),
            new RelationSeed("Brand voice", "parallels", "Voice", 4),
            new RelationSeed("Brand voice", "is built with", "Storytelling", 4),
            new RelationSeed("Storytelling", "uses", "Narrative arc", 5),
            new RelationSeed("Narrative arc", "is shaped by", "Character motivation", 4),
            new RelationSeed("Unreliable narrator", "plays with", "Voice", 4),
            new RelationSeed("Unreliable narrator", "complicates", "Character motivation", 3),
            new RelationSeed("Metaphor", "is a tool of", "Storytelling", 3),
            new RelationSeed("Metaphor", "is central to", "Astrological symbolism", 4),
            new RelationSeed("Archetype", "appears in", "Storytelling", 4),
            new RelationSeed("Archetype", "shapes", "Character motivation", 3),
            new RelationSeed("Astrological symbolism", "draws on", "Archetype", 4),
            new RelationSeed("Astrological symbolism", "is the language of", "Birth chart", 4),
            new RelationSeed("Birth chart", "is used to explore", "Self-concept", 4),
            new RelationSeed("Transit", "is read against", "Birth chart", 5),
            new RelationSeed("Saturn return", "is a kind of", "Transit", 5),
            new RelationSeed("Mercury retrograde", "is a kind of", "Transit", 5),
            new RelationSeed("Mercury retrograde", "invites", "Reflection", 4),
            new RelationSeed("Lunar cycle", "is tracked through", "Transit", 3),
            new RelationSeed("Lunar cycle", "is a natural cycle like", "Circadian rhythm", 3),
            new RelationSeed("Intention setting", "parallels", "Goal setting", 4),
            new RelationSeed("Intention setting", "is practised alongside", "Visualization", 4),
            new RelationSeed("Scripting", "is a form of", "Storytelling", 4),
            new RelationSeed("Scripting", "uses", "Narrative arc", 3),
            new RelationSeed("Scripting", "includes", "Reflection", 3),
            new RelationSeed("Scripting", "supports", "Identity shift", 4),
            new RelationSeed("Affirmation", "supports", "Identity shift", 3),
            new RelationSeed("Affirmation", "relates to", "Self-affirmation theory", 3),
            new RelationSeed("Identity shift", "changes", "Self-concept", 5),
            new RelationSeed("Identity shift", "is described through", "Narrative identity", 4),
            new RelationSeed("Visualization", "feeds", "Identity shift", 3)
        );
    }
}
