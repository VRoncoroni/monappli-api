package fr.exemple.monappli.synthese;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GenererSyntheseTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 1, 1);

    @TempDir
    Path tmp;

    /** Chaque dossier de tests/cases : input/ en entrée, expected/ comparé octet pour octet. */
    static Stream<Path> cas() throws IOException {
        return Files.list(Path.of("tests/cases")).filter(Files::isDirectory).sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cas")
    void jeuxDEssai(Path cas) throws IOException {
        GenererSynthese.generer(cas.resolve("input"), tmp, REFERENCE);
        assertEquals(
            Files.readString(cas.resolve("expected/synthese.csv"), StandardCharsets.UTF_8),
            Files.readString(tmp.resolve("synthese.csv"), StandardCharsets.UTF_8));
    }

    @Test
    void statuts() {
        assertEquals("NON-AFFECTE", GenererSynthese.statut(0));
        assertEquals("PARTIEL", GenererSynthese.statut(99));
        assertEquals("COMPLET", GenererSynthese.statut(100));
        assertEquals("SURCHARGE", GenererSynthese.statut(101));
    }

    @Test
    void latin1EstDetecte() {
        byte[] latin1 = "Hélène".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("Hélène", GenererSynthese.decoder(latin1));
        assertEquals("Hélène", GenererSynthese.decoder("Hélène".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void agentsAbsentRefuse() {
        var e = assertThrows(GenererSynthese.DonneesInvalides.class,
            () -> GenererSynthese.generer(tmp, tmp.resolve("out"), REFERENCE));
        assertTrue(e.getMessage().contains("agents.csv introuvable"));
    }

    @Test
    void enteteIncorrectRefuse() throws IOException {
        Files.writeString(tmp.resolve("agents.csv"), "id;nom\n1;Durand\n");
        var e = assertThrows(GenererSynthese.DonneesInvalides.class,
            () -> GenererSynthese.generer(tmp, tmp.resolve("out"), REFERENCE));
        assertTrue(e.getMessage().contains("en-tête attendu"));
    }

    @Test
    void matriculeEnDoubleRefuse() throws IOException {
        Files.writeString(tmp.resolve("agents.csv"), "matricule;nom;prenom;service\nA1;X;Y;Z\nA1;X;Y;Z\n");
        var e = assertThrows(GenererSynthese.DonneesInvalides.class,
            () -> GenererSynthese.generer(tmp, tmp.resolve("out"), REFERENCE));
        assertTrue(e.getMessage().contains("ligne 3 : matricule en double A1"), e.getMessage());
    }

    @Test
    void quotiteHorsBornesRefusee() throws IOException {
        Files.writeString(tmp.resolve("agents.csv"), "matricule;nom;prenom;service\nA1;X;Y;Z\n");
        Files.writeString(tmp.resolve("affectations.csv"), "matricule;projet;debut;fin;quotite\nA1;P;2025-01-01;;150\n");
        var e = assertThrows(GenererSynthese.DonneesInvalides.class,
            () -> GenererSynthese.generer(tmp, tmp.resolve("out"), REFERENCE));
        assertTrue(e.getMessage().contains("quotité invalide"), e.getMessage());
    }
}
