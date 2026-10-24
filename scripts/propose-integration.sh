#!/bin/sh
# Ouvre une MR (GitLab) ou une PR (GitHub) dans le repo Ansible pour déployer le candidat en intégration.
# Entrées : VERSION et BUILD (job build), ANSIBLE_REPO, ANSIBLE_REPO_TOKEN.
# AUTO_MERGE=false garde un merge humain (par défaut : fusion automatique si le plan passe).
set -eu
NEW="$VERSION-$BUILD"
FILE=inventories/integration/group_vars/all/versions.yml
BRANCH="maj/monappli-api-$NEW"
TITLE="monappli-api $NEW en intégration"
SOCLE_URL=$(scripts/socle-url.sh)

rm -rf socle
git clone -q --depth 1 "$SOCLE_URL" socle
cd socle
git config user.name  "ci-bot"
git config user.email "ci-bot@noreply.exemple.local"
git switch -c "$BRANCH"
# Modification ciblée de la seule ligne de version (yq -i reformaterait tout le fichier),
# puis contrôle de la valeur avec yq : la PR ne contient qu'une ligne.
sed -i "/^  - name: monappli-api\$/{n;s/^\(    version: \).*/\1$NEW/}" "$FILE"
ACTUAL=$(yq -r '.app_packages[] | select(.name == "monappli-api") | .version' "$FILE")
if [ "$ACTUAL" != "$NEW" ]; then
  echo "Impossible de mettre monappli-api à $NEW dans $FILE (format inattendu)"; exit 1
fi
if git diff --quiet; then
  echo "L'intégration est déjà en $NEW : rien à proposer."; exit 0
fi
git commit -qam "$TITLE"

if [ -n "${GITHUB_ACTIONS:-}" ]; then
  git push -q origin "HEAD:refs/heads/$BRANCH"
  export GH_TOKEN="$ANSIBLE_REPO_TOKEN"
  gh label create candidat --repo "$ANSIBLE_REPO" --color 1D76DB \
    --description "Nouveau candidat d'une application" 2>/dev/null || true
  gh pr create --repo "$ANSIBLE_REPO" --base main --head "$BRANCH" --label candidat \
    --title "$TITLE" \
    --body "Candidat publié dans yum-candidates par ${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID} (commit ${GITHUB_SHA})."
  if [ "${AUTO_MERGE:-true}" = true ]; then
    # Fusion automatique quand les contrôles requis passent (auto-merge activé sur le repo)
    gh pr merge --repo "$ANSIBLE_REPO" "$BRANCH" --auto --merge --delete-branch \
      || echo "Auto-merge indisponible (réglage du repo) : la PR attend un merge humain."
  fi
else
  # Les options -o font créer la merge request par GitLab au moment du push.
  # merge_when_pipeline_succeeds : fusion automatique si le plan passe. AUTO_MERGE=false pour un merge humain.
  AUTO=""
  [ "${AUTO_MERGE:-true}" = true ] && AUTO="-o merge_request.merge_when_pipeline_succeeds"
  # shellcheck disable=SC2086
  git push origin "HEAD:refs/heads/$BRANCH" \
    -o merge_request.create \
    -o merge_request.target=main \
    -o merge_request.title="$TITLE" \
    -o merge_request.label=candidat \
    $AUTO \
    -o merge_request.remove_source_branch
fi
