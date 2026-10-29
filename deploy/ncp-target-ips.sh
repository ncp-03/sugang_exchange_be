#!/usr/bin/env bash
# NCP Load Balancer Target Group에 지금 붙어 있는 서버의 사설 IP를 한 줄에 하나씩 출력한다.
# 오토스케일링으로 늘어난 서버까지 배포 대상에 넣기 위해 사용한다.
#
# 필요 환경변수: NCP_ACCESS_KEY, NCP_SECRET_KEY, NCP_TARGET_GROUP_NO
# 선택: NCP_REGION (기본 KR), NCP_API_HOST (기본 ncloud.apigw.ntruss.com)
# API: VPC Load Balancer getTargetList (조회 권한만 필요)
set -euo pipefail

: "${NCP_ACCESS_KEY:?NCP_ACCESS_KEY 없음}"
: "${NCP_SECRET_KEY:?NCP_SECRET_KEY 없음}"
: "${NCP_TARGET_GROUP_NO:?NCP_TARGET_GROUP_NO 없음}"
REGION="${NCP_REGION:-KR}"
HOST="${NCP_API_HOST:-ncloud.apigw.ntruss.com}"

uri="/vloadbalancer/v2/getTargetList?regionCode=${REGION}&targetGroupNo=${NCP_TARGET_GROUP_NO}&responseFormatType=json"
ts=$(( $(date +%s) * 1000 ))
sig=$(printf 'GET %s\n%s\n%s' "$uri" "$ts" "$NCP_ACCESS_KEY" \
      | openssl dgst -sha256 -hmac "$NCP_SECRET_KEY" -binary | base64 | tr -d '\n')

body=$(curl -fsS --max-time 20 "https://${HOST}${uri}" \
  -H "x-ncp-apigw-timestamp: ${ts}" \
  -H "x-ncp-iam-access-key: ${NCP_ACCESS_KEY}" \
  -H "x-ncp-apigw-signature-v2: ${sig}") || { echo "NCP API 호출 실패" >&2; exit 1; }

# jq 없이 targetIp 값만 뽑는다 (러너 서버에 jq가 없을 수 있음)
ips=$(grep -oE '"targetIp"[[:space:]]*:[[:space:]]*"[0-9.]+"' <<<"$body" | grep -oE '[0-9]+(\.[0-9]+){3}' | sort -u || true)
if [ -z "$ips" ]; then
  echo "응답에 targetIp가 없음: $(cut -c1-300 <<<"$body")" >&2
  exit 1
fi
echo "$ips"
