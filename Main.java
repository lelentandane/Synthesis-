package com.lelentandane.synthesis;

import com.lelentandane.synthesis.db.Database;
import com.lelentandane.synthesis.service.GraphService;
import com.lelentandane.synthesis.web.WebServer;
import java.nio.file.Path;

/** Starts Synthesis. Usage: java -jar synthesis.jar [--port 8080] [--db data/synthesis.db] [--host 127.0.0.1] */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String host = "127.0.0.1";
        Path databaseFile = Path.of("data", "synthesis.db");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(value(args, ++i, "--port"));
                case "--host" -> host = value(args, ++i, "--host");
                case "--db" -> databaseFile = Path.of(value(args, ++i, "--db"));
                case "--help", "-h" -> {
                    System.out.println("Usage: java -jar synthesis.jar [--port 8080] [--db data/synthesis.db] [--host 127.0.0.1]");
                    return;
                }
                default -> {
                    System.err.println("Unknown option: " + args[i] + " (try --help)");
                    System.exit(2);
                }
            }
        }

        Database database = Database.open(databaseFile);
        WebServer server = new WebServer(new GraphService(database), host, port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            database.close();
        }));

        System.out.println("Synthesis is running at http://" + host + ":" + server.port());
        System.out.println("Data file: " + databaseFile.toAbsolutePath());
        System.out.println("Press Ctrl+C to stop.");
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            System.err.println(option + " needs a value");
            System.exit(2);
        }
        return args[index];
    }
}
