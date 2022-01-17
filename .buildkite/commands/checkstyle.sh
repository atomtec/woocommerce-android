#!/bin/bash -eu

echo "--- ⤵️ Installing Bash Cache Plugin"
cp -v /bash-cache/bin/* /usr/bin

echo "---"
echo $BUILDKITE_PIPELINE_SLUG
echo $BUILDKITE_LABEL
echo "---"

CACHEKEY="${BUILDKITE_PIPELINE_SLUG}-${BUILDKITE_LABEL}"

echo "---"
echo $CACHEKEY
echo $CACHE_BUCKET_NAME
echo "---"

echo "--- 🧹 Linting"

restore_cache "$CACHEKEY"

cp -v gradle.properties-example gradle.properties
./gradlew checkstyle

save_cache /root/.android $CACHEKEY
ls /root
ls /root/.android