#!/bin/sh
# Rejoue les jeux d'essai de tests/cases avec le jar construit (target/monappli-api.jar).
# Même format de cas que les jeux d'essai du repo ansible-socle : input/ + expected/.
set -u
export MONAPPLI_JAR="${MONAPPLI_JAR:-target/monappli-api.jar}"
fail=0
for case in tests/cases/*/; do
  out=$(mktemp -d)
  if ./bin/generer-synthese --input "$case/input" --output "$out" --date-reference 2026-01-01 \
     && diff -ru "$case/expected" "$out"; then
    echo "OK     $case"
  else
    echo "ÉCHEC  $case"; fail=1
  fi
  rm -rf "$out"
done
exit "$fail"
