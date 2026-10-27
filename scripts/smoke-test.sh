#!/usr/bin/env bash
# 실행 중인 서버에 주요 API를 호출해 본다.
# 사용법: bash scripts/smoke-test.sh [서버주소]   (기본 http://localhost:8080)
# NCP 배포 후에도 그대로 쓸 수 있다: bash scripts/smoke-test.sh http://<서버IP>:8080
set -u
BASE="${1:-http://localhost:8080}"
FAIL=0

echo "서버 기동 대기: $BASE/actuator/health"
for i in $(seq 1 60); do
  if curl -fs "$BASE/actuator/health" | grep -q '"UP"'; then echo "UP (${i}초)"; break; fi
  if [ "$i" = 60 ]; then echo "서버가 60초 안에 뜨지 않았습니다"; exit 1; fi
  sleep 1
done

check() { # check <설명> <기대 HTTP 코드> <응답에 있어야 할 글자> <curl 인자...>
  local name="$1" want="$2" needle="$3"; shift 3
  local body code
  body=$(curl -s -w $'\n%{http_code}' "$@")
  code="${body##*$'\n'}"; body="${body%$'\n'*}"
  if [ "$code" = "$want" ] && grep -qF -- "$needle" <<<"$body"; then
    echo "OK   $name ($code)"
  else
    echo "FAIL $name: HTTP $code, 기대 $want, '$needle' 포함 여부 확인 실패"
    echo "     응답: ${body:0:300}"
    FAIL=1
  fi
}

check "강의 목록"            200 '"totalElements":21'        "$BASE/api/courses"
check "강의 검색(한글)"      200 '"scheduleText":"화목 15:00-16:15"' -G "$BASE/api/courses" --data-urlencode "keyword=프로그래밍"
check "강의 상세"            200 '"offeringId":9'            "$BASE/api/courses/9"
check "없는 강의"            404 'COURSE_NOT_FOUND'          "$BASE/api/courses/999"
check "필터 선택지"          200 '"departments"'             "$BASE/api/courses/filters"
check "학생 목록"            200 '"nickname":"졸린고양이"'    "$BASE/api/students"
# 로그인(학번 2024000001, 초기 비밀번호=학번) → 토큰
login_json=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d '{"studentNo":"2024000001","password":"2024000001"}')
TOKEN=$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' <<<"$login_json")
if [ -z "$TOKEN" ]; then echo "FAIL 로그인: 토큰을 받지 못함. 응답: ${login_json:0:300}"; FAIL=1; fi

check "로그인 실패(비밀번호 틀림)" 401 'INVALID_CREDENTIALS' -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" -d '{"studentNo":"2024000001","password":"wrong"}'
check "내 정보"              200 '"currentCredits":15'       -H "Authorization: Bearer $TOKEN" "$BASE/api/students/me"
check "내 수강 목록"         200 '"totalCredits":15'         -H "Authorization: Bearer $TOKEN" "$BASE/api/enrollments/me"
check "토큰 없이 내 수강 목록" 401 'MISSING_TOKEN'           "$BASE/api/enrollments/me"
check "가짜 토큰"            401 'INVALID_TOKEN'             -H "Authorization: Bearer abc.def" "$BASE/api/enrollments/me"
check "OpenAPI 문서"         200 '/api/enrollments/me'       "$BASE/v3/api-docs"
check "Swagger UI"           200 'swagger-ui'                "$BASE/swagger-ui/index.html"

if [ "$FAIL" = 0 ]; then echo "모든 확인 통과"; else echo "실패한 항목이 있습니다"; exit 1; fi
