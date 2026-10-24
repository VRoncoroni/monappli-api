package fr.exemple.monappli.synthese;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Générateur de synthèse : lit agents.csv et affectations.csv, écrit synthese.csv.
 *
 * <pre>
 * generer-synthese --input DOSSIER --output DOSSIER [--date-reference AAAA-MM-JJ]
 * </pre>
 *
 * <ul>
 *   <li>Séparateur « ; », première ligne d'en-tête obligatoire, pas de guillemets.</li>
 *   <li>Entrées en UTF-8 ou, à défaut, en ISO-8859-1 ; sortie toujours en UTF-8 avec des fins de ligne LF.</li>
 *   <li>affectations.csv est facultatif : sans lui, aucun agent n'est affecté.</li>
 *   <li>Sortie triée par matricule ; la date de référence fige le calcul (jeux d'essai déterministes).</li>
 * </ul>
 *
 * Codes retour : 0 succès, 2 usage, 3 données invalides.
 */
public final class GenererSynthese {

    static final String AGENTS = "agents.csv";
    static final String AFFECTATIONS = "affectations.csv";
    static final String SYNTHESE = "synthese.csv";

    static final String ENTETE_AGENTS = "matricule;nom;prenom;service";
    static final String ENTETE_AFFECTATIONS = "matricule;projet;debut;fin;quotite";
    static final String ENTETE_SYNTHESE = "matricule;nom;prenom;service;affectations_actives;quotite_totale;statut";

    record Agent(String matricule, String nom, String prenom, String service) {
    }

    /** Erreur de données : message destiné à l'utilisateur, code retour 3. */
    static final class DonneesInvalides extends RuntimeException {
        DonneesInvalides(String message) {
            super(message);
        }
    }

    private GenererSynthese() {
    }

    public static void main(String[] args) {
        Path input = null;
        Path output = null;
        LocalDate reference = LocalDate.now();
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--input" -> input = Path.of(args[++i]);
                    case "--output" -> output = Path.of(args[++i]);
                    case "--date-reference" -> reference = LocalDate.parse(args[++i]);
                    default -> throw new IllegalArgumentException("option inconnue : " + args[i]);
                }
            }
            if (input == null || output == null) {
                throw new IllegalArgumentException("--input et --output sont obligatoires");
            }
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException | DateTimeParseException e) {
            System.err.println("erreur : " + e.getMessage());
            System.err.println("usage : generer-synthese --input DOSSIER --output DOSSIER [--date-reference AAAA-MM-JJ]");
            System.exit(2);
        }

        try {
            generer(input, output, reference);
        } catch (DonneesInvalides e) {
            System.err.println("erreur : " + e.getMessage());
            System.exit(3);
        } catch (IOException | UncheckedIOException e) {
            System.err.println("erreur d'entrée-sortie : " + e.getMessage());
            System.exit(3);
        }
    }

    static void generer(Path input, Path output, LocalDate reference) throws IOException {
        Map<String, Agent> agents = lireAgents(input.resolve(AGENTS));
        Map<String, int[]> totaux = new TreeMap<>();   // matricule -> {nombre, quotité}
        Path affectations = input.resolve(AFFECTATIONS);
        if (Files.exists(affectations)) {
            cumulerAffectations(affectations, reference, agents, totaux);
        }

        StringBuilder out = new StringBuilder(ENTETE_SYNTHESE).append('\n');
        for (Agent a : agents.values()) {
            int[] t = totaux.getOrDefault(a.matricule(), new int[] {0, 0});
            out.append(String.join(";", a.matricule(), a.nom(), a.prenom(), a.service(),
                    Integer.toString(t[0]), Integer.toString(t[1]), statut(t[1])))
               .append('\n');
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve(SYNTHESE), out, StandardCharsets.UTF_8);
    }

    static String statut(int quotite) {
        if (quotite == 0) {
            return "NON-AFFECTE";
        }
        if (quotite < 100) {
            return "PARTIEL";
        }
        return quotite == 100 ? "COMPLET" : "SURCHARGE";
    }

    private static Map<String, Agent> lireAgents(Path fichier) throws IOException {
        if (!Files.exists(fichier)) {
            throw new DonneesInvalides(AGENTS + " introuvable dans " + fichier.getParent());
        }
        Map<String, Agent> agents = new TreeMap<>();   // tri stable par matricule
        List<String[]> lignes = lire(fichier, ENTETE_AGENTS, 4);
        for (int i = 0; i < lignes.size(); i++) {
            String[] c = lignes.get(i);
            if (agents.putIfAbsent(c[0], new Agent(c[0], c[1], c[2], c[3])) != null) {
                throw new DonneesInvalides(AGENTS + " ligne " + (i + 2) + " : matricule en double " + c[0]);
            }
        }
        return agents;
    }

    private static void cumulerAffectations(Path fichier, LocalDate reference,
                                            Map<String, Agent> agents, Map<String, int[]> totaux) throws IOException {
        List<String[]> lignes = lire(fichier, ENTETE_AFFECTATIONS, 5);
        for (int i = 0; i < lignes.size(); i++) {
            String[] c = lignes.get(i);
            String ou = AFFECTATIONS + " ligne " + (i + 2);
            if (!agents.containsKey(c[0])) {
                System.err.println("avertissement : " + ou + " : matricule inconnu " + c[0] + ", ligne ignorée");
                continue;
            }
            LocalDate debut = date(c[2], ou);
            LocalDate fin = c[3].isEmpty() ? null : date(c[3], ou);
            int quotite = quotite(c[4], ou);
            boolean active = !debut.isAfter(reference) && (fin == null || !fin.isBefore(reference));
            if (active) {
                int[] t = totaux.computeIfAbsent(c[0], k -> new int[2]);
                t[0]++;
                t[1] += quotite;
            }
        }
    }

    /** Lit un CSV, vérifie l'en-tête et renvoie les lignes de données découpées. */
    private static List<String[]> lire(Path fichier, String entete, int colonnes) throws IOException {
        String contenu = decoder(Files.readAllBytes(fichier));
        if (contenu.startsWith("﻿")) {
            contenu = contenu.substring(1);
        }
        List<String> lignes = contenu.lines().toList();
        String nom = fichier.getFileName().toString();
        if (lignes.isEmpty() || !lignes.get(0).strip().equalsIgnoreCase(entete)) {
            throw new DonneesInvalides(nom + " : en-tête attendu « " + entete + " »");
        }
        List<String[]> donnees = new ArrayList<>();
        for (int i = 1; i < lignes.size(); i++) {
            if (lignes.get(i).isBlank()) {
                continue;
            }
            String[] c = lignes.get(i).split(";", -1);
            if (c.length != colonnes) {
                throw new DonneesInvalides(nom + " ligne " + (i + 1) + " : " + colonnes + " colonnes attendues, " + c.length + " trouvées");
            }
            for (int j = 0; j < c.length; j++) {
                c[j] = c[j].strip();
            }
            donnees.add(c);
        }
        return donnees;
    }

    /** UTF-8 si les octets sont valides, sinon ISO-8859-1 (fichiers produits par d'anciens outils). */
    static String decoder(byte[] octets) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(octets))
                .toString();
        } catch (CharacterCodingException e) {
            return new String(octets, StandardCharsets.ISO_8859_1);
        }
    }

    private static LocalDate date(String valeur, String ou) {
        try {
            return LocalDate.parse(valeur);
        } catch (DateTimeParseException e) {
            throw new DonneesInvalides(ou + " : date invalide « " + valeur + " » (attendu AAAA-MM-JJ)");
        }
    }

    private static int quotite(String valeur, String ou) {
        try {
            int q = Integer.parseInt(valeur);
            if (q < 0 || q > 100) {
                throw new NumberFormatException();
            }
            return q;
        } catch (NumberFormatException e) {
            throw new DonneesInvalides(ou + " : quotité invalide « " + valeur + " » (entier de 0 à 100)");
        }
    }
}
