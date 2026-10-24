#!/bin/sh
# Le paquet n'active ni ne démarre le service : c'est le rôle d'Ansible
systemctl daemon-reload >/dev/null 2>&1 || true
exit 0
