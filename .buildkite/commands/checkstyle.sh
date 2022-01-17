#!/bin/bash -eu

echo "--- ⤵️ Installing Bash Cache Plugin"
cp -v /bash-cache/bin/* /usr/bin

apt install -y awscli jq


echo "--- 🧹 Linting"
CACHEKEY="${BUILDKITE_PIPELINE_SLUG}-${BUILDKITE_LABEL}"
restore_cache "$CACHEKEY"

cp -v gradle.properties-example gradle.properties
./gradlew checkstyle

save_cache /root/.android $CACHEKEY
