#!/bin/sh
# Refuse de construire une version déjà publiée en release.
# usage : scripts/check-version-free.sh 2.4.1
set -eu
if git ls-remote --exit-code --tags origin "refs/tags/v$1" >/dev/null 2>&1; then
  echo "La version $1 est déjà publiée (tag v$1). Passez à la version suivante dans pom.xml."
  exit 1
fi
echo "Version $1 libre : aucun tag v$1."
