#!/usr/bin/env bash
set -euo pipefail
# Keep the internal authentication key alongside the persistent database.
if [ ! -s /data/db/.replica-key ]; then
  umask 077
  head -c 756 /dev/urandom | base64 > /data/db/.replica-key
fi
chmod 600 /data/db/.replica-key
chown mongodb:mongodb /data/db/.replica-key
exec docker-entrypoint.sh mongod --replSet rs0 --keyFile /data/db/.replica-key --bind_ip_all
