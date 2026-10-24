#!/bin/sh
# Affiche l'URL authentifiée du repo ansible-socle, selon la forge qui exécute le job.
#   ANSIBLE_REPO        chemin du projet : infra/ansible-socle (GitLab), VRoncoroni/ansible-socle (GitHub)
#   ANSIBLE_REPO_TOKEN  jeton du robot : rôle Developer + write_repository (GitLab),
#                       fine-grained PAT contents + pull requests en écriture (GitHub)
set -eu
if [ -n "${GITHUB_ACTIONS:-}" ]; then
  printf 'https://x-access-token:%s@%s/%s.git\n' "$ANSIBLE_REPO_TOKEN" "${GITHUB_SERVER_URL#https://}" "$ANSIBLE_REPO"
else
  printf 'https://ci-bot:%s@%s/%s.git\n' "$ANSIBLE_REPO_TOKEN" "$CI_SERVER_HOST" "$ANSIBLE_REPO"
fi
