#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
release_dir="${1:?Usage: bash deployment/package-release.sh /absolute/staging-directory}"
mkdir -p "$release_dir/release/agent"
jar="$repo_root/smart-diy-pc-assistant-backend/target/smart-diy-pc-assistant-backend-0.0.1-SNAPSHOT.jar"
if unzip -l "$jar" | grep -q 'application-local.y'; then
  echo 'Refusing to package a JAR containing private application-local configuration.' >&2
  exit 1
fi
cp "$jar" "$release_dir/release/backend.jar"
cp "$repo_root/deployment/compose.production.yaml" "$release_dir/compose.yaml"
cp "$repo_root/deployment/backend.release.Dockerfile" "$release_dir/release/backend.Dockerfile"
cp "$repo_root/deployment/frontend.release.Dockerfile" "$release_dir/release/frontend.Dockerfile"
for frontend in web admin; do
  mkdir -p "$release_dir/release/$frontend"
  cp -R "$repo_root/smart-diy-pc-assistant-$frontend/dist/." "$release_dir/release/$frontend/"
done
for item in app scripts Dockerfile requirements.lock pyproject.toml README.md; do
  cp -R "$repo_root/smart-diy-pc-assistant-agent/$item" "$release_dir/release/agent/"
done
# No runtime files, private env, database snapshots or Python caches in image contexts.
find "$release_dir/release/agent" -type d -name __pycache__ -prune -exec rm -r {} +
for pair in web:18088 admin:18089; do
  name="${pair%%:*}"; port="${pair##*:}"
  sed -e "s/listen 80;/listen $port;/" -e 's|http://backend:8080|http://127.0.0.1:8080|g' -e 's|http://admin:80|http://127.0.0.1:18089|g' \
    "$repo_root/deployment/nginx.conf" > "$release_dir/nginx-$name.conf"
done
printf 'Release staged at %s. Existing server secrets and persistent volumes are not part of this package.\n' "$release_dir"
