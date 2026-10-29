#!/usr/bin/env bash
# Vue 서버(course-vue-01)에서 root로 한 번만 실행한다.
#   ssh ncp-vue 'bash -s' < deploy/setup-vue-artifacts.sh
#
# 하는 일: 배포 워크플로가 최신 jar를 올려 둘 폴더와, API 서브넷에서만 받아갈 수 있는 nginx 포트(8008)를 만든다.
#   - 8008은 SELinux 기본 http_port_t에 포함된 포트라 semanage 설정이 필요 없다.
#   - API 서브넷(10.0.5.0/24) 외에는 nginx가 403으로 막는다. ACG에서도 인바운드 8008은 API 서브넷만 허용할 것.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "root로 실행하세요" >&2; exit 1; }

API_SUBNET="${API_SUBNET:-10.0.5.0/24}"
DIR=/var/www/artifacts/course-change

mkdir -p "$DIR"
# 셀프호스티드 러너(gha 계정)가 있으면 그 계정이 직접 쓸 수 있게
if id gha >/dev/null 2>&1; then
  chown -R gha:gha /var/www/artifacts
else
  chown -R root:root /var/www/artifacts
fi
chmod 755 /var/www/artifacts "$DIR"

cat > /etc/nginx/conf.d/artifacts.conf <<EOF
# 배포 워크플로가 올린 최신 API jar를 API 서버(ASG 포함)가 부팅 시 받아간다. 사설망 전용.
server {
    listen 8008;
    server_name _;
    root /var/www/artifacts;
    autoindex off;
    access_log /var/log/nginx/artifacts.access.log;

    allow ${API_SUBNET};
    allow 127.0.0.1;
    deny all;

    location /course-change/ {
        try_files \$uri =404;
    }
    location / { return 404; }
}
EOF

command -v restorecon >/dev/null 2>&1 && restorecon -R /var/www/artifacts || true
nginx -t
systemctl reload nginx
echo "OK: http://$(hostname -I | awk '{print $1}'):8008/course-change/ (허용: ${API_SUBNET})"
