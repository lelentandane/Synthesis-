# Synthesis

![Tests](https://github.com/lelentandane/synthesis/actions/workflows/tests.yml/badge.svg)

Synthesis is a concept graph that answers one question: **how are these two ideas connected?**

You add ideas from any field, link them with named relationships, and Synthesis finds the route between any two of them, explains it in plain sentences, and analyses the whole graph to show which ideas hold it together and which links look like they are missing.

It is a Java 21 application with a JSON API, a SQLite database and a browser interface. There is no framework: routing, validation and error handling are all in the code you can read.

![The demo graph](docs/screenshots/graph.png)

## The problem

Note apps store ideas but do not tell you how two of them connect, or which connections are missing. Synthesis turns a set of ideas and named relationships into a graph, finds and explains the route between any two ideas, and points out the ideas that hold the graph together and the links that look like they should exist.

**Built for:** students, researchers and writers who work across several fields and want to see how their ideas relate.

## What this project demonstrates

- **Data structures and algorithms in real use:** breadth-first search, Dijkstra's algorithm, Brandes' betweenness centrality and Jaccard similarity, each tested on small graphs with known answers.
- **Java 21 without a framework:** the JDK's built-in HTTP server, routing, validation and JSON handling written by hand.
- **Database design:** SQLite through JDBC with schema constraints, transactions, and an import that either fully succeeds or leaves the graph untouched.
- **Web security basics:** JSON-only writes, request size limits, a static file whitelist, a strict Content-Security-Policy, and no HTML built from user text.
- **Testing:** JUnit 5 tests from algorithm level up to the real HTTP API.

## What it does

**Find the connection between two ideas**

- Fewest steps (breadth-first search) or strongest links (Dijkstra's algorithm), plus up to three alternative routes.
- The answer is written as sentences that follow the real direction of each relationship, for example: *Mercury retrograde invites Reflection. Reflection deepens Narrative identity. Narrative identity is expressed through Storytelling. Brand voice is built with Storytelling.*
- The route is highlighted on the graph with numbered steps.

![A route across disciplines](docs/screenshots/connect.png)

**Understand the whole graph**

- **Keystone concepts:** betweenness centrality (Brandes' algorithm) finds the ideas that sit on the most shortest routes between others.
- **Missing links:** unlinked pairs that share at least two neighbours, ranked by Jaccard similarity, each with a one-click "Link them".
- **Where disciplines meet:** how many relationships cross from one field into another.
- **Clusters and orphans:** separate groups with no link between them, and concepts with no links at all.

![Insights](docs/screenshots/insights.png)

**Work with it**

- A force-directed layout written from scratch (repulsion, springs, gravity), drawn on a canvas with pan, zoom, pinch and drag.
- Add, edit and delete concepts and links. Disciplines are colour-coded and can be hidden or shown.
- Search, export the graph as JSON, import it again, or load a ready-made demo graph that spans six fields.
- Keyboard-accessible controls, a text view of everything on the canvas, and a layout that works on a phone.

## Run it

You need **Java 21 or newer** and **Maven 3.9 or newer**.

```bash
git clone https://github.com/lelentandane/synthesis.git
cd synthesis

mvn package
java -jar target/synthesis.jar
```

Open http://127.0.0.1:8080 and choose "Load the demo graph".

Options: `--port 8080`, `--db data/synthesis.db`, `--host 127.0.0.1`. Your graph is stored in the database file, so it is still there next time you start the app.

## Run the tests

```bash
mvn test
```

The suite covers the graph algorithms on small graphs with known answers, the service rules and validation against a real in-memory SQLite database, and the full HTTP API by starting the real server on a free port and calling it.

## How it works

**Data model.** Concepts and relations live in SQLite. Concept names are unique ignoring case, relations cascade when a concept is deleted, and strength is constrained to 1 to 5 in the schema itself. All SQL is in the two repository classes and every query is parameterised.

**Graph.** `KnowledgeGraph` builds an adjacency list. Relations are stored with a direction but searched as two-way links. Edges are sorted strongest first, so every algorithm gives the same answer on every run.

**Paths.**
- `fewestSteps` is a breadth-first search, O(V + E).
- `strongest` is Dijkstra's algorithm with cost = 6 minus strength, so a strong link is a cheap hop. Ties are broken by fewer hops.
- `alternatives` is a depth-first search for other simple routes, limited by length and by a work budget so it cannot run away on dense graphs.

**Analysis.** Betweenness uses Brandes' algorithm, O(V * E). Clusters use breadth-first search. Suggestions compare neighbour sets with Jaccard similarity.

**Service layer.** `GraphService` holds the rules (trimming, case-insensitive uniqueness, length limits, no self-links, no duplicate links). Importing validates the whole file first and writes it in a single transaction, so a bad file cannot leave the graph half-imported.

**Web layer.** Built on the HTTP server that ships with the JDK. It requires `Content-Type: application/json` on writes (which blocks cross-site form posts), limits request size, serves only a whitelist of static file types, and sends a strict Content-Security-Policy. The front end builds all page content with `textContent`, never as HTML.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/graph` | All concepts and relations |
| POST | `/api/concepts` | Create a concept |
| PUT / DELETE | `/api/concepts/{id}` | Edit or delete a concept |
| POST | `/api/relations` | Create a link |
| DELETE | `/api/relations/{id}` | Delete a link |
| GET | `/api/path?from=&to=&mode=` | Route between two concepts (`fewest` or `strongest`) |
| GET | `/api/insights` | Keystones, clusters, discipline links, suggestions, orphans |
| POST | `/api/demo` | Load the demo graph (empty graphs only) |
| GET | `/api/export` | Download the graph as JSON |
| POST | `/api/import` | Replace the graph from an exported file |

Errors come back as JSON, for example `{"error": "A concept named \"Habit loop\" already exists."}`, with a fitting status code (400, 404, 405, 409, 413, 415).

## Project structure

```
src/main/java/com/lelentandane/synthesis/
  Main.java                  Starts the app
  model/                     Concept and Relation records
  db/                        SQLite connection, transactions, repositories
  graph/                     KnowledgeGraph, PathFinder, Insights
  service/                   GraphService (rules) and DemoData
  web/                       HTTP server, routing, JSON mapping
src/main/resources/
  schema.sql                 Database tables
  static/                    Browser interface (HTML, CSS, JavaScript, fonts)
src/test/java/               JUnit 5 tests
pom.xml                      Maven build
```

## Possible next steps

- Weighted import from CSV and Markdown notes
- Named graphs, so you can keep separate maps
- Community detection to colour clusters automatically
- Move the web layer to a framework such as Spring Boot, keeping the same service and graph code

## Author

**Sphelele Ntandane**
[GitHub](https://github.com/Lelentandane) | [LinkedIn](https://www.linkedin.com/in/sphelele-ntandane-9a7178382)

## Licence

MIT. Bundled fonts (Instrument Serif and Hanken Grotesk) are under the SIL Open Font License, see `src/main/resources/static/fonts`.
