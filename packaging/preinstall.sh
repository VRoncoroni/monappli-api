#!/bin/sh
# Crée le compte technique monappli (idempotent)
getent group monappli >/dev/null || groupadd -r monappli
getent passwd monappli >/dev/null || \
  useradd -r -g monappli -d /opt/monappli -s /sbin/nologin monappli
exit 0
