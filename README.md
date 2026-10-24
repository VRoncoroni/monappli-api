# monappli-api

Petite API Java d'exemple (Javalin) et son générateur de synthèse, avec la chaîne complète :
tests, build, RPM signé, publication dans Nexus comme **candidat**, ouverture automatique de la PR de
déploiement dans [ansible-socle](https://github.com/VRoncoroni/ansible-socle), puis promotion du même
fichier en **release**.

```
PR ──▶ build : compilation, tests unitaires, jeux d'essai              (rien n'est publié)
merge sur main ──▶ build ──▶ package (RPM signé) ──▶ yum-candidates ──▶ PR « candidat » dans ansible-socle
                                                       │
           intégration ─▶ preprod (dans ansible-socle) │
                                                       ▼
release (manuel) : preprod tourne bien ce build ? ─▶ même RPM, même sha256 ─▶ yum-releases + tag vX.Y.Z
```

## Candidat et release

Un **candidat** est un build publié, déployable en intégration et en preprod, pas encore validé.
Une **release** est un candidat validé en preprod, copié tel quel (mêmes octets, même empreinte)
dans le dépôt des releases, le seul que la prod peut lire.

| | Candidat | Release |
|---|---|---|
| Produit par | chaque merge sur `main`, automatiquement | le job manuel `release`, sur le candidat choisi |
| Identifiant | `2.4.1-38` : version du code, puis numéro de build | le même `2.4.1-38`, plus le tag `v2.4.1` et une release |
| Dépôt Nexus | `yum-candidates` | `yum-releases` |
| Déployable en | integration, preprod | integration, preprod, prod |
| Durée de vie | purgé après 90 jours sans téléchargement | conservée |

**On construit une fois, on promeut le fichier.** Pas de `2.4.1-rc.1` reconstruit en `2.4.1` : le RPM
qui part en prod est celui qui a passé l'intégration et la preprod, et le job de release le vérifie
par son empreinte sha256. Le numéro de build est le champ *Release* du RPM
(`monappli-api-2.4.1-38.noarch.rpm`) : `$CI_PIPELINE_IID` sur GitLab, `github.run_number` sur GitHub.
Si le candidat 38 est refusé, la correction donne le 39, avec la même version 2.4.1.

## Contenu

```
monappli-api/
├── pom.xml                        # version 2.4.1, sans -SNAPSHOT ; Java 21 ; jar unique (shade)
├── ci/settings.xml                # Maven passe par le proxy Nexus (maven-public)
├── ci/images/                     # Dockerfiles des images d'outillage GitLab
├── src/main/java/fr/exemple/monappli/
│   ├── App.java                   # /health et /version
│   ├── ConfigCheck.java           # « monappli --check-config » (validate: d'Ansible)
│   └── synthese/GenererSynthese.java
├── src/test/java/…                # tests unitaires + jeux d'essai rejoués en JUnit
├── bin/                           # lanceurs installés dans /opt/monappli/bin
├── tests/cases/                   # jeux d'essai : input/ + expected/
├── tests/run-cases.sh             # rejoue les jeux d'essai avec le jar construit
├── packaging/
│   ├── nfpm.yaml                  # description du RPM
│   ├── monappli-api.service       # unit systemd livrée par le paquet
│   ├── preinstall.sh              # crée le compte monappli
│   └── postinstall.sh             # daemon-reload ; ne démarre rien (rôle d'Ansible)
├── scripts/
│   ├── check-version-free.sh      # refuse une version déjà publiée (tag vX.Y.Z)
│   ├── propose-integration.sh     # ouvre la MR/PR dans ansible-socle
│   ├── release.sh                 # copie le candidat dans les releases
│   └── socle-url.sh               # URL authentifiée d'ansible-socle (GitLab ou GitHub)
├── .gitlab-ci.yml                 # pipeline GitLab
└── .github/workflows/             # ci.yml (build → candidat → PR), release.yml (manuel)
```

## L'application

| Point d'entrée | Rôle |
|---|---|
| `GET /health` | `{"status":"UP"}` — contrôle de santé du playbook |
| `GET /version` | `{"version":"2.4.1-38","environment":"preprod"}` — preuve de version du playbook (`Implementation-Version` du manifeste ; `dev` en local) |
| `/opt/monappli/bin/monappli-api` | lanceur du service ; exige `JAVA_HOME` (fourni par la surcharge systemd d'Ansible) |
| `/opt/monappli/bin/monappli --check-config FICHIER` | valide `app.conf` avant qu'Ansible ne l'écrive |
| `/opt/monappli/bin/generer-synthese --input D --output D [--date-reference AAAA-MM-JJ]` | lit `agents.csv` (+ `affectations.csv` facultatif), écrit `synthese.csv` |

La configuration arrive en variables d'environnement (`/etc/monappli/api.env`, généré par Ansible) :
`APP_PORT`, `APP_ENV`, `APP_LOG_LEVEL`.

**Le générateur de synthèse** : séparateur `;`, en-têtes obligatoires, entrées en UTF-8 (BOM accepté)
ou ISO-8859-1, sortie en UTF-8 avec fins de ligne LF, triée par matricule. Une affectation est active
si `debut ≤ date de référence ≤ fin` (fin vide = en cours). Statut : `NON-AFFECTE`, `PARTIEL` (< 100 %),
`COMPLET`, `SURCHARGE` (> 100 %). Codes retour : 0, 2 (usage), 3 (données invalides).

## En local

```sh
mvn verify                       # compilation + tests (dont les jeux d'essai en JUnit)
tests/run-cases.sh               # jeux d'essai avec le jar construit (target/monappli-api.jar)

APP_PORT=8080 JAVA_HOME=/chemin/du/jdk21 MONAPPLI_JAR=target/monappli-api.jar bin/monappli-api
curl localhost:8080/version

VERSION=2.4.1 BUILD=0 nfpm package --config packaging/nfpm.yaml --packager rpm --target dist/
```

Pour jouer tout le parcours (Nexus, trois VM, promotion, release) : le lab d'ansible-socle,
`lab/lab.sh build` et `lab/lab.sh release`.

## Les pipelines

### GitLab (`.gitlab-ci.yml`)

| Job | Quand | Ce qu'il fait |
|---|---|---|
| `build` | MR et main | refuse une version déjà publiée, `mvn verify` avec `-Dbuild.number=$CI_PIPELINE_IID`, jeux d'essai, rapport JUnit, `build.env` (VERSION, BUILD, RPM_FILE) |
| `package` | main | RPM signé avec nfpm + empreinte sha256 (artefact gardé un an) |
| `publish:candidate` | main | envoi dans `yum-candidates` (Nexus refuse un second envoi du même fichier) |
| `propose:integration` | main | MR dans ansible-socle, fusion automatique si le plan passe (`AUTO_MERGE=false` pour un merge humain) |
| `release` | main, **manuel** | contrôle que la preprod tourne ce build, vérifie le sha256, copie dans `yum-releases`, crée tag et release |

`release` est en `allow_failure: true` : le pipeline reste vert et le bouton reste disponible.
Les branches `release/*` produisent aussi des candidats (correctif d'une version déjà en prod).

### GitHub Actions

| Workflow | Quand | Jobs |
|---|---|---|
| `ci.yml` | PR, push sur `main` et `release/**` | `build` → `package` → `publish-candidate` → `propose-integration` |
| `release.yml` | **Run workflow**, avec le candidat (ex. `2.4.1-38`) | retrouve le run de build, relit son empreinte, lance `scripts/release.sh` ; release GitHub avec le RPM attaché |

Sans `NEXUS_URL`, le build passe par Maven Central et s'arrête au RPM (artefact du run) : rien n'est
publié ni proposé. L'environnement GitHub `release`, avec reviewers requis, porte la décision humaine.

## Variables et secrets

| GitLab | GitHub | Contenu | Options |
|---|---|---|---|
| `NEXUS_URL` | variable `NEXUS_URL` | `https://nexus.exemple.local` | — |
| `NEXUS_CI_USER` / `_PASSWORD` | secrets (environnements `candidates` et `release`) | compte qui écrit dans `yum-candidates` | masquée, protégée |
| `NEXUS_RELEASE_USER` / `_PASSWORD` | secrets (environnement `release`) | compte qui écrit dans `yum-releases`, utilisé par le seul job release | masquée, protégée |
| `RPM_SIGNING_KEY` | secret (contenu de la clé) | clé GPG privée de signature (type fichier sur GitLab) | protégée |
| `NFPM_RPM_PASSPHRASE` | secret | phrase de passe de cette clé | masquée, protégée |
| `ANSIBLE_REPO_TOKEN` | secret | GitLab : jeton du projet ansible-socle, rôle Developer, `write_repository`. GitHub : PAT fine-grained sur ansible-socle, *Contents* et *Pull requests* en écriture | masquée, protégée |
| `RELEASE_TOKEN` | — (`GITHUB_TOKEN`, `contents: write`) | GitLab : jeton de ce projet, rôle Maintainer, portée `api` | masquée, protégée |
| `ANSIBLE_REPO` (dans le YAML) | variable `ANSIBLE_REPO` | `infra/ansible-socle` / `VRoncoroni/ansible-socle` | — |

Protégez les tags `v*` (GitLab : Settings › Repository › Protected tags ; GitHub : règle de tags) :
une release ne se crée que par le job release.

## Les dépôts Nexus

| Dépôt | Type et réglages | Écriture | Lecture | Nettoyage |
|---|---|---|---|---|
| `maven-public` | group : proxy de Maven Central | aucune | builds CI | cache du proxy |
| `yum-candidates` | yum hosted, *Repodata Depth* 0, *Disable redeploy* | `svc-ci-monappli` | intégration, preprod, job release | composants non téléchargés depuis 90 jours |
| `yum-releases` | yum hosted, mêmes réglages | `svc-release` (job release seulement) | les trois envs | aucun |
| `raw-outils` | raw hosted (JDK, clé GPG publique) | équipe ops | les trois envs | manuel |

*Disable redeploy* rend les versions immuables ; *Repodata Depth* 0 permet de ranger les RPM par
application (`monappli-api/…`) sans changer l'URL déclarée sur les VM. Le compte de release a besoin
des droits `add` et `edit` sur `yum-releases`.

## Après une release

- Le tag `v2.4.1` existe : tout nouveau build en 2.4.1 est refusé dès la PR. La PR suivante passe le
  `pom.xml` en 2.4.2 (correctif), 2.5.0 (évolution compatible) ou 3.0.0 (rupture).
- Correctif sur une version en prod alors que `main` a avancé : branche protégée `release/2.4` depuis
  le tag `v2.4.1`, passée en 2.4.2 ; elle produit des candidats comme `main`.
- Un candidat refusé ne se « dépublie » pas : il reste dans `yum-candidates` jusqu'à sa purge, le
  suivant le remplace dans le `versions.yml` de l'intégration.
- Si une version exige une autre version de Java (`maven.compiler.release`), modifiez `java_version`
  dans ansible-socle dans le même mouvement : un jar Java 21 ne démarre pas sur un JDK 17.
