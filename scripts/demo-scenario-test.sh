#!/usr/bin/env bash
# 시연 시나리오 리허설 (CI 자동 실행용, 로컬에서도 사용 가능)
#   1) tools/db/seed_demo.sql 로 시연 DB를 초기화하고
#   2) 발표 시나리오를 API로 그대로 재현한다:
#      고양이 프언01 신청 → 마감(409) → 웹프01 신청 성공
#      → 고양이 '웹프01 내놓기 × 프언01 받기' 대기 → 다람쥐 '프언01 내놓기 × 웹프01 받기' 즉시 매칭
#      → 양쪽 확정 → 고양이 프언01 / 다람쥐 웹프01
#   3) 끝나면 시드를 다시 실행해 처음 상태로 돌려 둔다.
# 사용법: MYSQL="mysql -h127.0.0.1 -ucourse_change -pcourse_change course_change" \
#         bash scripts/demo-scenario-test.sh [서버주소]
set -euo pipefail
BASE="${1:-http://localhost:8080}"
MYSQL="${MYSQL:-mysql -h127.0.0.1 -P3306 -ucourse_change -pcourse_change course_change}"
SEED="$(dirname "$0")/../tools/db/seed_demo.sql"

fail() { echo "FAIL $*"; exit 1; }
ok()   { echo "OK   $*"; }

echo "== 시드 실행"
$MYSQL < "$SEED" > /dev/null

login() { # login <학번> → 토큰
  curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"studentNo\":\"$1\",\"password\":\"$1\"}" | jq -r .accessToken
}
call() { # call <토큰> <메서드> <경로> [JSON] → "HTTP코드\n본문"
  local t="$1" m="$2" p="$3" d="${4:-}"
  if [ -n "$d" ]; then
    curl -s -w '\n%{http_code}' -X "$m" "$BASE$p" -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d "$d"
  else
    curl -s -w '\n%{http_code}' -X "$m" "$BASE$p" -H "Authorization: Bearer $t"
  fi
}
code() { tail -n1 <<<"$1"; }
body() { sed '$d' <<<"$1"; }

CAT=$(login 2024000001);  [ -n "$CAT" ] && [ "$CAT" != null ] || fail "고양이 로그인"
SQ=$(login 2024000002);   [ -n "$SQ" ]  && [ "$SQ"  != null ] || fail "다람쥐 로그인"

r=$(call "$CAT" GET /api/enrollments/me)
[ "$(body "$r" | jq '.enrollments | length')" = 0 ] && ok "고양이 수강 0건으로 시작" || fail "고양이 수강 0건 아님: $(body "$r" | head -c 300)"

r=$(call "$CAT" GET /api/wishlists/me)
[ "$(body "$r" | jq '[.wishlists[] | .course.offeringId]' -c)" = '[9,18,12,3,7]' ] \
  && ok "고양이 희과담 5과목 (프언01이 맨 위)" || fail "고양이 희과담: $(body "$r" | head -c 300)"

r=$(call "$CAT" POST /api/enrollments '{"offeringId":9}')
[ "$(code "$r")" = 409 ] && ok "프언01 신청 → 정원 마감 (409 $(body "$r" | jq -r .code))" || fail "프언01이 마감이 아님: $r"

r=$(call "$CAT" POST /api/enrollments '{"offeringId":18}')
[ "$(code "$r")" = 201 ] || fail "웹프01 신청 실패: $r"
WEB=$(body "$r" | jq -r .enrollmentId); ok "웹프01 신청 성공 (enrollment $WEB)"

r=$(call "$CAT" POST /api/exchange-requests "{\"offeredEnrollmentId\":$WEB,\"wantedOfferingIds\":[9],\"autoMatch\":true}")
[ "$(code "$r")" = 201 ] || [ "$(code "$r")" = 200 ] || fail "고양이 교환 요청: $r"
[ "$(body "$r" | jq -r .status)" = WAITING ] && ok "고양이 '웹프01 → 프언01' 대기열 등록" || fail "고양이 요청이 WAITING이 아님: $(body "$r")"

PL=$($MYSQL -N -e "SELECT enrollment_id FROM enrollments WHERE student_id = 2 AND offering_id = 9")
r=$(call "$SQ" POST /api/exchange-requests "{\"offeredEnrollmentId\":$PL,\"wantedOfferingIds\":[18],\"autoMatch\":true}")
[ "$(code "$r")" = 201 ] || [ "$(code "$r")" = 200 ] || fail "다람쥐 교환 요청: $r"
SID=$(body "$r" | jq -r .matchSessionId)
[ "$(body "$r" | jq -r .status)" = MATCHED ] && [ "$SID" != null ] && ok "다람쥐 '프언01 → 웹프01' 즉시 매칭 (세션 $SID)" \
  || fail "다람쥐 요청이 즉시 매칭되지 않음: $(body "$r")"

r=$(call "$CAT" POST "/api/match-sessions/$SID/accept"); [ "$(code "$r")" = 200 ] || fail "고양이 확정: $r"
r=$(call "$SQ"  POST "/api/match-sessions/$SID/accept"); [ "$(code "$r")" = 200 ] || fail "다람쥐 확정: $r"
ok "양쪽 확정 (세션 상태 $(body "$r" | jq -r .status))"

r=$(call "$CAT" GET /api/enrollments/me)
[ "$(body "$r" | jq -c '[.enrollments[].course.offeringId] | sort')" = '[9]' ] && ok "고양이 수강: 프언01" || fail "고양이 수강: $(body "$r" | head -c 300)"
r=$(call "$SQ" GET /api/enrollments/me)
body "$r" | jq -e '[.enrollments[].course.offeringId] | (index(18) != null) and (index(9) == null)' >/dev/null \
  && ok "다람쥐 수강: 웹프01 받음, 프언01 내놓음" || fail "다람쥐 수강: $(body "$r" | head -c 300)"

echo "== 시드 다시 실행 (처음 상태로)"
$MYSQL < "$SEED" > /dev/null
echo "시연 시나리오 통과"
