package fr.exemple.monappli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vérificateur de /etc/monappli/app.conf, appelé par Ansible avant d'écrire le fichier
 * (option validate: du module template) : {@code monappli --check-config <fichier>}.
 * Code retour 0 si le fichier est valide, 1 sinon, avec la liste des erreurs sur stderr.
 */
public final class ConfigCheck {

    private static final Set<String> LOG_LEVELS = Set.of("trace", "debug", "info", "warn", "error");

    private ConfigCheck() {
    }

    public static void main(String[] args) {
        if (args.length != 2 || !"--check-config".equals(args[0])) {
            System.err.println("usage : monappli --check-config <fichier>");
            System.exit(2);
        }
        List<String> errors;
        try {
            errors = check(Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8));
        } catch (IOException e) {
            errors = List.of("lecture impossible (" + e + ")");
        }
        errors.forEach(e -> System.err.println(args[1] + " : " + e));
        System.exit(errors.isEmpty() ? 0 : 1);
    }

    static List<String> check(List<String> lines) {
        List<String> errors = new ArrayList<>();
        Map<String, Map<String, String>> sections = new HashMap<>();
        Map<String, String> current = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = sections.computeIfAbsent(line.substring(1, line.length() - 1).strip(), k -> new HashMap<>());
            } else if (current == null) {
                errors.add("ligne " + (i + 1) + " : clé hors de toute section");
            } else if (line.indexOf('=') <= 0) {
                errors.add("ligne " + (i + 1) + " : attendu « clé = valeur »");
            } else {
                int eq = line.indexOf('=');
                current.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
            }
        }

        String port = value(sections, "server", "port", errors);
        if (port != null && !isPort(port)) {
            errors.add("[server] port invalide : " + port);
        }
        String level = value(sections, "server", "log_level", errors);
        if (level != null && !LOG_LEVELS.contains(level)) {
            errors.add("[server] log_level invalide : " + level + " (attendu : " + String.join(", ", LOG_LEVELS) + ")");
        }
        value(sections, "database", "host", errors);
        value(sections, "runtime", "environment", errors);
        return errors;
    }

    private static String value(Map<String, Map<String, String>> sections, String section, String key, List<String> errors) {
        String v = sections.getOrDefault(section, Map.of()).get(key);
        if (v == null || v.isEmpty()) {
            errors.add("[" + section + "] " + key + " manquant");
            return null;
        }
        return v;
    }

    private static boolean isPort(String s) {
        try {
            int p = Integer.parseInt(s);
            return p > 0 && p < 65536;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
