#!/usr/bin/env bash
# Invoked by deploy.sh through the authorized non-root SSH account and sudo.
set -Eeuo pipefail
root="${1:?deployment root required}"
incoming="${2:?incoming bundle required}"
[[ "$root" =~ ^/www/[a-zA-Z0-9_/-]+$ && "$incoming" == "$root/incoming/"* ]] || exit 2
cd "$root"
exec 9> "$root/deployment.lock"
flock -n 9 || { echo 'Another deployment is running.' >&2; exit 1; }
release_id="$(cat "$incoming/release-id")"
[[ "$release_id" =~ ^[a-zA-Z0-9_-]+$ ]] || exit 2
backup="$root/backups/release-$release_id"
mkdir -p "$backup"
chmod 700 "$backup"
nginx_bin=/www/server/nginx/sbin/nginx
nginx_conf=/www/server/panel/vhost/nginx/smartdiy-ipv6.conf
rollback_ready=0
rollback() {
  status=$?
  trap - ERR
  if [[ "$rollback_ready" == 1 ]]; then
    echo 'Release failed. Restoring the previous application images/configuration; persistent data is preserved.' >&2
    for service in backend agent web admin; do docker image inspect "smartdiy-$service:rollback" >/dev/null 2>&1 && docker tag "smartdiy-$service:rollback" "smartdiy-$service:current" || true; done
    for file in compose.yaml nginx-web.conf nginx-admin.conf; do [[ ! -f "$backup/$file" ]] || cp -p "$backup/$file" "$root/$file"; done
    if [[ -f "$backup/nginx-ipv6.conf" ]]; then cp -p "$backup/nginx-ipv6.conf" "$nginx_conf"; fi
    if [[ -d "$backup/release" ]]; then
      [[ ! -d "$root/release" ]] || mv "$root/release" "$backup/failed-release"
      cp -a "$backup/release" "$root/release"
    fi
    docker compose up -d --force-recreate --no-build --wait --wait-timeout 90 || true
    if [[ -x "$nginx_bin" ]]; then "$nginx_bin" -t && "$nginx_bin" -s reload || true; fi
  fi
  echo "FAILED (exit $status). Backup: $backup" >&2
  exit "$status"
}
trap rollback ERR
# A disabled systemd unit can still be active/retrying and steal the host port.
if systemctl cat cct-smartdiy.service >/dev/null 2>&1; then
  systemctl stop cct-smartdiy.service
  systemctl disable cct-smartdiy.service
  echo 'PASS legacy direct Java service stopped and disabled'
fi
python3 - <<'PYPORT'
import json,re,subprocess
raw=subprocess.run(['docker','inspect','smartdiy-backend-1'],capture_output=True,text=True)
expected=json.loads(raw.stdout)[0]['State']['Pid'] if raw.returncode==0 else 0
listeners=subprocess.check_output(['ss','-ltnp','sport = :8080'],text=True)
pids={int(p) for p in re.findall(r'pid=(\d+)',listeners)}
if pids-{expected}:raise SystemExit('Port 8080 is occupied outside the application backend container; resolve the conflict before deployment.')
print('PASS backend port ownership')
PYPORT
# Do not interrupt an in-flight user task during a routine release.
for attempt in $(seq 1 60); do
  active="$(python3 - "$root" <<'PY'
import sqlite3,sys
from pathlib import Path
p=Path(sys.argv[1])/'agent-data/agent.sqlite'
if not p.exists(): print(0)
else:
 with sqlite3.connect('file:'+str(p)+'?mode=ro',uri=True) as db: print(db.execute("SELECT count(*) FROM runs WHERE status IN ('queued','running')").fetchone()[0])
PY
)"
  [[ "$active" == 0 ]] && break
  [[ "$attempt" != 60 ]] || { echo 'User tasks are still active; retry deployment later.' >&2; exit 1; }
  echo "Waiting for $active active Agent task(s)..."
  sleep 5
done
python3 - "$root" "$backup" <<'PY'
import sqlite3,sys
from pathlib import Path
root,backup=map(Path,sys.argv[1:])
for name in ['agent.sqlite','checkpoints.sqlite']:
 p=root/'agent-data'/name
 if p.exists():
  with sqlite3.connect(p) as src,sqlite3.connect(backup/name) as dst:src.backup(dst)
PY
for file in compose.yaml nginx-web.conf nginx-admin.conf deployed-commit; do [[ ! -f "$root/$file" ]] || cp -p "$root/$file" "$backup/$file"; done
[[ ! -f "$nginx_conf" ]] || cp -p "$nginx_conf" "$backup/nginx-ipv6.conf"
for service in backend agent web admin; do docker image inspect "smartdiy-$service:current" >/dev/null && docker tag "smartdiy-$service:current" "smartdiy-$service:rollback"; done
rollback_ready=1
if [[ -d "$root/release" ]]; then mv "$root/release" "$backup/release"; fi
cp -a "$incoming/release" "$root/release"
for file in compose.yaml nginx-web.conf nginx-admin.conf; do cp -p "$incoming/$file" "$root/$file"; done
cp -p "$incoming/nginx-ipv6.conf.template" "$root/nginx-ipv6.conf.template"
# Host-specific package mirror is a non-secret deployment preference.
export PIP_INDEX_URL="${PIP_INDEX_URL:-https://pypi.tuna.tsinghua.edu.cn/simple}"
docker compose build
docker compose up -d --force-recreate --wait --wait-timeout 120
if [[ -f "$root/ipv6-hostname" ]]; then
  python3 - "$root" "$nginx_conf" <<'PY'
from pathlib import Path
import re,sys
root=Path(sys.argv[1]);host=(root/'ipv6-hostname').read_text().strip()
if not re.fullmatch(r'[a-z0-9.-]+',host):raise SystemExit('Invalid direct hostname')
p=root/'ipv6-https-port';port=int(p.read_text().strip()) if p.exists() else 443
cert=root/'certs'/host
if not (cert/'fullchain.pem').exists() or not (cert/'key.pem').exists():raise SystemExit('Direct TLS certificate is missing')
s=(root/'nginx-ipv6.conf.template').read_text().replace('__DIRECT_HOST__',host).replace('__CERT_DIR__',str(cert)).replace('__HTTPS_PORT__',str(port)).replace('__HTTPS_SUFFIX__','' if port==443 else ':'+str(port))
Path(sys.argv[2]).write_text(s)
PY
  "$nginx_bin" -t
  "$nginx_bin" -s reload
fi
probe() {
  label="$1"; shift
  for path in / /admin/ /api/health /agent/health; do
    code="$(curl --retry 2 --retry-delay 2 --connect-timeout 10 --max-time 25 -sS -o /dev/null -w '%{http_code}' "$@" "$origin$path")"
    [[ "$code" == 200 ]] || { echo "$label $path failed: HTTP $code" >&2; return 1; }
    echo "PASS $label $path HTTP $code"
  done
  for path in /release-info.json /admin/release-info.json; do
    manifest="$(curl --retry 2 --connect-timeout 10 --max-time 25 -fsS "$@" "$origin$path")"
    printf '%s' "$manifest" | python3 -c 'import json,sys; assert json.load(sys.stdin)["release_id"]==sys.argv[1], "Release version mismatch"' "$release_id"
    echo "PASS $label $path release $release_id"
  done
  if [[ "$origin" == https://* ]]; then
    cors="$(curl --retry 2 --connect-timeout 10 --max-time 25 -sS -D - -o /dev/null -X OPTIONS -H "Origin: $origin" -H 'Access-Control-Request-Method: POST' "$@" "$origin/api/auth/login")"
    printf '%s' "$cors" | tr -d '\r' | grep -Fiq "Access-Control-Allow-Origin: $origin"
    echo "PASS $label browser-origin preflight"
  fi
}
origin=http://127.0.0.1:18088;probe 'container origin' --noproxy '*'
origin=https://smartdiy.cctstudio.cn;probe 'Cloudflare Tunnel' -4
if [[ -f "$root/ipv6-hostname" ]]; then
  direct_host="$(cat "$root/ipv6-hostname")";direct_port="$(cat "$root/ipv6-https-port" 2>/dev/null || echo 443)"
  suffix='';[[ "$direct_port" == 443 ]] || suffix=":$direct_port"
  origin="https://$direct_host$suffix";probe 'IPv6 direct' -6 --noproxy '*'
  direct_ip="$(python3 - "$direct_host" <<'PYIP'
import socket,sys
print(socket.getaddrinfo(sys.argv[1],None,socket.AF_INET6,socket.SOCK_STREAM)[0][4][0])
PYIP
)"
  if curl -6 --noproxy '*' --connect-timeout 5 --max-time 10 --resolve "unapproved-smartdiy.invalid:$direct_port:[$direct_ip]" -sS "https://unapproved-smartdiy.invalid:$direct_port/" >/dev/null 2>&1; then
    echo 'Unexpected hostname was accepted by the direct TLS endpoint.' >&2; exit 1
  fi
  rejection="$(timeout 10 openssl s_client -connect "[$direct_ip]:$direct_port" -servername unapproved-smartdiy.invalid -showcerts </dev/null 2>&1 || true)"
  if printf '%s' "$rejection" | grep -q 'BEGIN CERTIFICATE'; then echo 'Direct default TLS endpoint leaked a certificate for an unexpected hostname.' >&2; exit 1; fi
  no_sni="$(timeout 10 openssl s_client -connect "[$direct_ip]:$direct_port" -noservername -showcerts </dev/null 2>&1 || true)"
  if printf '%s' "$no_sni" | grep -q 'BEGIN CERTIFICATE'; then echo 'Direct TLS endpoint presented a certificate without SNI.' >&2; exit 1; fi
  if curl -6 --noproxy '*' --connect-timeout 5 --max-time 10 -fsS -H 'Host: unapproved-smartdiy.invalid' "$origin/" >/dev/null 2>&1; then
    echo 'Unexpected HTTP Host was accepted by the direct endpoint.' >&2; exit 1
  fi
  echo 'PASS IPv6 direct rejects unexpected TLS SNI and HTTP Host without presenting a certificate' 
fi
cp "$incoming/source-revision" "$root/deployed-commit"
printf '%s\n' "$backup" > "$root/last-release-backup"
docker compose ps --format '{{.Service}} {{.State}} {{.Health}}'
trap - ERR
echo "SUCCESS: $release_id; backup: $backup"
rm -rf -- "$incoming"
