#!/usr/bin/env bash
# course-change 서비스가 시작되기 직전(ExecStartPre)에 root로 실행된다.
# Vue 서버(사설망)에 배포 워크플로가 올려 둔 최신 jar와 해시를 비교해서, 다르면 받아서 교체한다.
#
# 목적: 오토스케일링(ASG)이 옛 이미지로 새 서버를 띄워도, 서비스가 켜지는 순간 최신 jar로 바뀌게 한다.
# 원칙: 어떤 이유로든 받아오기에 실패하면 기존 jar로 그냥 시작한다 (항상 exit 0). 서비스 시작을 막지 않는다.
#
# 받는 주소: /etc/course-change.env 의 JAR_SOURCE_URL (없으면 기본값)
#   ${JAR_SOURCE_URL}/app.jar, ${JAR_SOURCE_URL}/app.jar.sha256
set -u

APP_DIR=/opt/course-change
JAR="$APP_DIR/app.jar"
SRC="${JAR_SOURCE_URL:-http://10.0.1.8:8008/course-change}"
TAG="[fetch-latest-jar]"

log() { echo "$TAG $*"; }

# 배포 워크플로가 롤백할 때는 이 파일을 만들어 두고 재시작한다 (롤백한 jar를 다시 덮어쓰지 않도록)
if [ -f "$APP_DIR/.skip-fetch" ]; then
  log "skip (.skip-fetch 있음)"
  rm -f "$APP_DIR/.skip-fetch"
  exit 0
fi

remote_sha=$(curl -fsS --connect-timeout 3 --max-time 10 "$SRC/app.jar.sha256" 2>/dev/null | awk '{print $1}' | tr -d '\r\n')
if ! [[ "$remote_sha" =~ ^[0-9a-f]{64}$ ]]; then
  log "최신 해시를 못 받음 ($SRC) → 기존 jar로 시작"
  exit 0
fi

local_sha=""
[ -f "$JAR" ] && local_sha=$(sha256sum "$JAR" | awk '{print $1}')
if [ "$local_sha" = "$remote_sha" ]; then
  log "이미 최신 (${remote_sha:0:12})"
  exit 0
fi

tmp="$APP_DIR/app.jar.download"
rm -f "$tmp"
if ! curl -fsS --connect-timeout 3 --max-time 120 -o "$tmp" "$SRC/app.jar"; then
  log "jar 다운로드 실패 → 기존 jar로 시작"
  rm -f "$tmp"; exit 0
fi
got_sha=$(sha256sum "$tmp" | awk '{print $1}')
if [ "$got_sha" != "$remote_sha" ]; then
  log "해시 불일치 (받은 ${got_sha:0:12} / 기대 ${remote_sha:0:12}) → 기존 jar로 시작"
  rm -f "$tmp"; exit 0
fi

chown root:course "$tmp" 2>/dev/null || true
chmod 640 "$tmp"
[ -f "$JAR" ] && cp -p "$JAR" "$APP_DIR/app.jar.prev"
mv -f "$tmp" "$JAR"
log "교체 완료 ${local_sha:0:12} → ${remote_sha:0:12}"
exit 0
