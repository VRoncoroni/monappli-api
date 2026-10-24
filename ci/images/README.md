# Images d'outillage de la CI GitLab

Le `.gitlab-ci.yml` utilise des images internes `registry.exemple.local/outillage/…`, construites une
fois avec des outils figés, puis poussées dans le registre interne. Un build de l'image ne dépend
ainsi jamais d'un téléchargement au moment du pipeline.

| Image                       | Fichier                    | Contenu                      |
|-----------------------------|----------------------------|------------------------------|
| `maven:3.9-temurin-21`      | `maven.Dockerfile`         | Maven 3.9 + JDK Temurin 21   |
| `nfpm:2`                    | `nfpm.Dockerfile`          | nfpm 2.x (fabrication RPM)   |
| `curl:8`                    | `curl.Dockerfile`          | curl                         |
| `git-yq-curl:1`             | `git-yq-curl.Dockerfile`   | git, yq (mikefarah), curl    |

```sh
docker build -f ci/images/nfpm.Dockerfile -t registry.exemple.local/outillage/nfpm:2 ci/images
docker push registry.exemple.local/outillage/nfpm:2
```
