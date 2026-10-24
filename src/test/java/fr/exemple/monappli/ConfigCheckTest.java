package fr.exemple.monappli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigCheckTest {

    private static final List<String> VALIDE = List.of(
        "# Ansible managed",
        "[server]",
        "port = 8080",
        "log_level = debug",
        "",
        "[database]",
        "host = db-int.exemple.local",
        "include = /etc/monappli/secrets.conf",
        "",
        "[runtime]",
        "environment = integration");

    @Test
    void fichierValideAccepte() {
        assertEquals(List.of(), ConfigCheck.check(VALIDE));
    }

    @Test
    void portInvalideRefuse() {
        var lignes = VALIDE.stream().map(l -> l.startsWith("port") ? "port = 99999" : l).toList();
        assertEquals(List.of("[server] port invalide : 99999"), ConfigCheck.check(lignes));
    }

    @Test
    void niveauDeLogInconnuRefuse() {
        var lignes = VALIDE.stream().map(l -> l.startsWith("log_level") ? "log_level = bavard" : l).toList();
        assertTrue(ConfigCheck.check(lignes).get(0).startsWith("[server] log_level invalide"));
    }

    @Test
    void sectionManquanteEtLigneMalFormeeSignalees() {
        var erreurs = ConfigCheck.check(List.of("orphelin = 1", "[server]", "port 8080"));
        assertTrue(erreurs.contains("ligne 1 : clé hors de toute section"), erreurs.toString());
        assertTrue(erreurs.contains("ligne 3 : attendu « clé = valeur »"), erreurs.toString());
        assertTrue(erreurs.contains("[runtime] environment manquant"), erreurs.toString());
    }
}
