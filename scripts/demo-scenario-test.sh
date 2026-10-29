#!/usr/bin/env bash
# 시연 시나리오 리허설 (CI 자동 실행용, 로컬에서도 사용 가능)
#   1) tools/db/seed_demo.sql 로 시연 DB를 초기화하고
#   2) 발표 시나리오를 API로 그대로 재현한다:
#      고양이 프언01 신청 → 마감(409) → 소공01 신청 → 프언02 신청 → 시간표 겹침(409)
#      → 웹프01 등 나머지 6과목 신청 → 19/19학점(상한) → 추가 신청 시 학점 상한 초과(409)
#      → 교환 가능 여부 표에 불가 사유('학점 상한 초과'·'시간표 겹침')가 함께 나오는지 확인
#      → 고양이 '웹프01 내놓기 × 프언01 받기' 대기 → 다람쥐 '프언01 내놓기 × 웹프01 받기' 즉시 매칭
#      → 양쪽 확정 → 고양이 프언01(소공01 등 6과목 유지, 19/19 그대로) / 다람쥐 웹프01
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
[ "$(body "$r" | jq '[.wishlists[] | .course.offeringId]' -c)" = '[9,12,10,1,11,13,16,21]' ] \
  && ok "고양이 희과담 8과목 (프언01이 맨 위)" || fail "고양이 희과담: $(body "$r" | head -c 300)"

r=$(call "$CAT" POST /api/enrollments '{"offeringId":9}')
[ "$(code "$r")" = 409 ] && [ "$(body "$r" | jq -r .code)" = COURSE_FULL ] \
  && ok "프언01 신청 → 정원 마감 (409 COURSE_FULL)" || fail "프언01이 마감이 아님: $r"

# 소공01을 먼저 잡아 두고 프언02를 신청하면 월수 13:30이 겹친다
r=$(call "$CAT" POST /api/enrollments '{"offeringId":12}')
[ "$(code "$r")" = 201 ] || fail "소공01 신청 실패: $r"
ok "소공01 신청 성공 (교환에 올리지 않고 끝까지 유지할 강의)"

r=$(call "$CAT" POST /api/enrollments '{"offeringId":10}')
[ "$(code "$r")" = 409 ] && [ "$(body "$r" | jq -r .code)" = SCHEDULE_CONFLICT ] \
  && ok "프언02 신청 → 소공01과 시간표 겹침 (409 SCHEDULE_CONFLICT)" || fail "프언02가 시간표 겹침이 아님: $r"

r=$(call "$CAT" POST /api/enrollments '{"offeringId":18}')
[ "$(code "$r")" = 201 ] || fail "웹프01 신청 실패: $r"
WEB=$(body "$r" | jq -r .enrollmentId); ok "웹프01 신청 성공 (enrollment $WEB)"

# 남은 5과목으로 학점 상한(19)을 꽉 채운다: 자료구조01·컴파일러01·이산수학01·대학영어02·체육01(1학점)
for off in 1 11 13 16 21; do
  r=$(call "$CAT" POST /api/enrollments "{\"offeringId\":$off}")
  [ "$(code "$r")" = 201 ] || fail "offering $off 신청 실패: $r"
done
ok "나머지 5과목 신청 성공"

r=$(call "$CAT" GET /api/enrollments/me)
[ "$(body "$r" | jq -r '.totalCredits')" = 19 ] && [ "$(body "$r" | jq -r '.maxCredits')" = 19 ] \
  && [ "$(body "$r" | jq -r '.count')" = 7 ] \
  && ok "고양이 수강 7과목 19/19학점 (상한 꽉 찬 상태)" || fail "고양이 학점이 19/19가 아님: $(body "$r" | head -c 300)"

# 꽉 찬 상태에서는 일반 신청으로 더 늘릴 수 없다 (컴퓨터네트워크 01: 정원·같은과목 검사는 통과)
r=$(call "$CAT" POST /api/enrollments '{"offeringId":5}')
[ "$(code "$r")" = 409 ] && [ "$(body "$r" | jq -r .code)" = CREDIT_LIMIT_EXCEEDED ] \
  && ok "꽉 찬 상태에서 추가 신청 → 학점 상한 초과 (409 CREDIT_LIMIT_EXCEEDED)" \
  || fail "학점 상한 초과가 아님: $r"

# 교환 가능 여부 표에 '가능'만 나오지 않고 불가 사유도 나오는지 (시연에서 보여 줄 부분)
r=$(call "$CAT" GET /api/exchange-requests/eligibility)
[ "$(code "$r")" = 200 ] || fail "교환 가능 여부 조회: $r"
body "$r" | grep -q "학점 상한 초과" \
  && ok "가능 여부 표에 '학점 상한 초과' 불가 사유 있음 (체육01 1학점 x 프언01 3학점)" \
  || fail "'학점 상한 초과' 불가 사유가 없음: $(body "$r" | head -c 500)"
body "$r" | grep -q "시간표 겹침" \
  && ok "가능 여부 표에 '시간표 겹침' 불가 사유 있음 (소공01이 남은 채로 프언02 받기)" \
  || fail "'시간표 겹침' 불가 사유가 없음: $(body "$r" | head -c 500)"

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
[ "$(body "$r" | jq -c '[.enrollments[].course.offeringId] | sort')" = '[1,9,11,12,13,16,21]' ] \
  && ok "고양이 수강: 웹프01 → 프언01 로 바뀜, 소공01 등 6과목 그대로" \
  || fail "고양이 수강: $(body "$r" | head -c 300)"
[ "$(body "$r" | jq -r '.totalCredits')" = 19 ] \
  && ok "교환 후에도 19/19학점 (학점 합계 보존)" || fail "교환 후 학점이 19가 아님: $(body "$r" | head -c 300)"
r=$(call "$SQ" GET /api/enrollments/me)
body "$r" | jq -e '[.enrollments[].course.offeringId] | (index(18) != null) and (index(9) == null)' >/dev/null \
  && ok "다람쥐 수강: 웹프01 받음, 프언01 내놓음" || fail "다람쥐 수강: $(body "$r" | head -c 300)"

echo "== 시드 다시 실행 (처음 상태로)"
$MYSQL < "$SEED" > /dev/null
echo "시연 시나리오 통과"
