#!/usr/bin/env bash
# Run from any directory. Passwords are read interactively and never saved.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
deploy_host="${DEPLOY_HOST:-gouc@192.168.1.6}"
deploy_root="${DEPLOY_ROOT:-/www/smartdiy-docker}"
[[ "$deploy_host" != root@* && "$deploy_root" =~ ^/www/[a-zA-Z0-9_/-]+$ ]] || { echo 'Invalid deployment target (root SSH is not allowed).' >&2; exit 1; }
staging="$(mktemp -d /tmp/smartdiy-publish.XXXXXX)"
ssh_options=(-o ControlMaster=auto -o ControlPersist=600 -o "ControlPath=$staging/ssh-control")
if [[ -n "${SSH_IDENTITY_FILE:-}" ]]; then ssh_options+=(-i "$SSH_IDENTITY_FILE" -o IdentitiesOnly=yes); fi
cleanup() {
  unset deploy_sudo_password
  ssh "${ssh_options[@]}" -O exit "$deploy_host" >/dev/null 2>&1 || true
  rm -rf -- "$staging"
}
trap cleanup EXIT
cd "$repo_root"
echo "Connecting to $deploy_host (existing SSH credentials or key)..."
ssh "${ssh_options[@]}" "$deploy_host" true
read -r -s -p 'Server sudo password: ' deploy_sudo_password
printf '\n'
[[ -n "$deploy_sudo_password" ]] || { echo 'A sudo password is required.' >&2; exit 1; }
source_revision="$(git rev-parse --short HEAD)"
if [[ -n "$(git status --porcelain)" ]]; then source_revision="$source_revision-dirty"; fi
release_id="$(date +%Y%m%d-%H%M%S)-$source_revision"
echo 'Running backend tests and building Java...'
(cd smart-diy-pc-assistant-backend && bash mvnw -q clean test package)
echo 'Checking Python dependencies and Agent tests...'
agent_python="$repo_root/smart-diy-pc-assistant-agent/.venv/bin/python"
if [[ ! -x "$agent_python" ]]; then python3 -m venv "$repo_root/smart-diy-pc-assistant-agent/.venv"; fi
requirements_hash="$(shasum -a 256 smart-diy-pc-assistant-agent/requirements.lock | cut -d ' ' -f1)"
installed_hash="$(cat smart-diy-pc-assistant-agent/.venv/.deployment-requirements 2>/dev/null || true)"
if [[ "$requirements_hash" != "$installed_hash" ]]; then
  "$agent_python" -m pip install -q -r smart-diy-pc-assistant-agent/requirements.lock
  printf '%s\n' "$requirements_hash" > smart-diy-pc-assistant-agent/.venv/.deployment-requirements
fi
"$agent_python" -m pytest smart-diy-pc-assistant-agent/tests -q
echo 'Building both Vue frontends...'
(cd smart-diy-pc-assistant-web && npm ci --no-audit --no-fund && npm test && npm run build)
(cd smart-diy-pc-assistant-admin && npm ci --no-audit --no-fund && VITE_PUBLIC_BASE=/admin/ npm run build)
bash deployment/package-release.sh "$staging/bundle"
cp deployment/deploy.remote.sh deployment/nginx-ipv6.conf.template "$staging/bundle/"
python3 - "$staging/bundle" "$source_revision" "$release_id" <<'PYINFO'
import json,sys
from pathlib import Path
root=Path(sys.argv[1]);data=json.dumps({'revision':sys.argv[2],'release_id':sys.argv[3]})
for app in ['web','admin']:(root/'release'/app/'release-info.json').write_text(data+'\n')
PYINFO
printf '%s\n' "$source_revision" > "$staging/bundle/source-revision"
printf '%s\n' "$release_id" > "$staging/bundle/release-id"
remote_staging="$deploy_root/incoming/$release_id"
ssh "${ssh_options[@]}" "$deploy_host" "mkdir -p '$remote_staging'"
printf -v remote_shell '%q ' ssh "${ssh_options[@]}"
rsync -az -e "$remote_shell" "$staging/bundle/" "$deploy_host:$remote_staging/"
echo 'Applying release, backing up data and checking both network entrances...'
printf '%s\n' "$deploy_sudo_password" | ssh "${ssh_options[@]}" "$deploy_host" "sudo -S -p '' bash '$remote_staging/deploy.remote.sh' '$deploy_root' '$remote_staging'"
echo "SUCCESS: $release_id (Tunnel + IPv6 use the same release)."
