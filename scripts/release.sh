#!/bin/sh
# Publie en release un candidat validé en preprod : même fichier, autre dépôt.
# Entrées : VERSION, BUILD, RPM_FILE, COMMIT_SHA (commit qui a produit le build),
#           dist/$RPM_FILE.sha256 (empreinte enregistrée au build),
#           NEXUS_URL, NEXUS_CI_USER/_PASSWORD, NEXUS_RELEASE_USER/_PASSWORD,
#           ANSIBLE_REPO, ANSIBLE_REPO_TOKEN, RELEASE_TOKEN (GitLab) ou GH_TOKEN (GitHub).
set -eu
NEW="$VERSION-$BUILD"
COMMIT_SHA="${COMMIT_SHA:-${CI_COMMIT_SHA:-}}"

# 1. Une version ne se publie qu'une fois
scripts/check-version-free.sh "$VERSION"

# 2. Le candidat doit être celui qui tourne en preprod
rm -rf socle
git clone -q --depth 1 "$(scripts/socle-url.sh)" socle
PREPROD=$(yq -r '.app_packages[] | select(.name == "monappli-api") | .version' \
  socle/inventories/preprod/group_vars/all/versions.yml)
if [ "$PREPROD" != "$NEW" ]; then
  echo "La preprod tourne en $PREPROD, pas en $NEW : release refusée."
  exit 1
fi

# 3. Récupérer le candidat dans Nexus et vérifier qu'il est identique au fichier construit
curl -fsS -u "$NEXUS_CI_USER:$NEXUS_CI_PASSWORD" -o "$RPM_FILE" \
  "$NEXUS_URL/repository/yum-candidates/monappli-api/$RPM_FILE"
# Le .sha256 nomme le fichier sans dossier : c'est le RPM téléchargé ici qui est contrôlé
sha256sum -c "dist/$RPM_FILE.sha256"
SHA256=$(cut -d' ' -f1 "dist/$RPM_FILE.sha256")

# 4. Copier dans les releases (Nexus refuse d'écraser une version existante : Disable redeploy)
curl -fsS -u "$NEXUS_RELEASE_USER:$NEXUS_RELEASE_PASSWORD" --upload-file "$RPM_FILE" \
  "$NEXUS_URL/repository/yum-releases/monappli-api/$RPM_FILE"

# 5. Tag vX.Y.Z et release sur le commit qui a produit ce build
NOTES="Build $BUILD, $RPM_FILE, sha256 $SHA256"
if [ -n "${GITHUB_ACTIONS:-}" ]; then
  gh release create "v$VERSION" "$RPM_FILE" "dist/$RPM_FILE.sha256" \
    --target "$COMMIT_SHA" --title "monappli-api $VERSION" --notes "$NOTES"
else
  # L'API des releases crée le tag au passage quand il n'existe pas encore
  curl -fsS --request POST --header "PRIVATE-TOKEN: $RELEASE_TOKEN" \
    --data-urlencode "tag_name=v$VERSION" \
    --data-urlencode "ref=$COMMIT_SHA" \
    --data-urlencode "name=monappli-api $VERSION" \
    --data-urlencode "description=$NOTES" \
    "$CI_API_V4_URL/projects/$CI_PROJECT_ID/releases" >/dev/null
fi

echo "Release $VERSION publiée (build $BUILD)."
