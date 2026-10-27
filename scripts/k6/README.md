# k6 부하 테스트 시나리오

이 스크립트는 현재 `wip/day22` 백엔드 API를 대상으로 합니다. 기본 인증은 `TOKEN`이며, 학생별로 `POST /api/auth/login`을 호출해 Bearer 토큰을 사용합니다. `APP_AUTH_MODE=DEMO`로 실행한 비공개 서버에서만 `-e AUTH_MODE=DEMO`를 사용하세요.

## 실행

저장소 루트에서:

```bash
k6 run -e PROFILE=smoke -e BASE_URL=http://127.0.0.1:8080 scripts/k6/scenarios.js
k6 run -e PROFILE=lt18 -e BASE_URL=http://127.0.0.1:8080 scripts/k6/scenarios.js
```

서버에 파일 하나만 내려받은 경우:

```bash
curl -fL https://raw.githubusercontent.com/ncp-03/sugang_exchange_be/wip/day22/scripts/k6/scenarios.js -o ~/scenarios.js
k6 run -e PROFILE=lt18 -e BASE_URL=http://127.0.0.1:8080 ~/scenarios.js
```

`lt18`은 1 VU 90초 → 32 VU 180초 → 1 VU 90초입니다. 중단은 Ctrl+C입니다. Cloud Insight의 Min1 집계에서 기준·폭증·회복 구간을 비교하세요. `SPIKE_VUS`, `BASELINE_VUS`, `SPIKE_DURATION`, `THINK_TIME`으로 부하를 조절할 수 있습니다. MobaXterm 로컬 터미널에서는 `bash scripts/cloud-insight-quick-load.sh http://SERVER_IP:8080`도 가능합니다.

기본 데모 계정은 학생 ID 1, 학번/초기 비밀번호 `2024000001`입니다. 다른 계정이면 `-e STUDENT_ID=... -e STUDENT_NO=... -e STUDENT_PASSWORD=...`를 지정하세요. 토큰 인증을 쓰는 서버에서 `X-Student-Id`만 보내면 401이 반환됩니다.

## 프로필과 범위

| 프로필 | 구현한 부하 | LT 범위 |
| --- | --- | --- |
| `smoke` | 강의·학생·수강 조회와 오류 응답 | 기본 API 확인 |
| `lt01` | 강의 목록·검색 | LT-01 |
| `lt02` | 지정 분반 집중 조회 | LT-02 일부 (인기도 지표 없음) |
| `lt03` | 서로 다른 학생의 교환 요청 동시 등록 | LT-03 |
| `lt04` | 같은 수강 내역에 동시 등록, 201 또는 중복 409 확인 | LT-04 |
| `lt05` | 대기열 순위 반복 조회 | LT-05의 조회·재계산 부분 |
| `lt06` | 서로 다른 쌍의 빠른 매칭 일괄 실행 | LT-06의 API 부분 |
| `lt07` | 동일 쌍의 매칭 경쟁, 200 또는 충돌 409 확인 | LT-07 일부 |
| `lt08` | 한 세션의 두 참여자 동의와 완료 확인 | LT-08 일부 |
| `lt09` | 현재 학점 합계·시간표 검사 | LT-09 일부 (신규 교환 검증 제외) |
| `lt11` | 세션·교환 요청·찜 목록 폴링 | LT-11 일부 (푸시 알림 제외) |
| `lt12`, `load` | 강의·학생·세션 조회 혼합 | LT-12의 조회 부분 |
| `lt16` | 장시간 조회 | LT-16의 조회 부분 |
| `lt17` | 단계별 조회 VU 증가 | LT-17의 조회 부분 |
| `lt18` | 조회 부하 폭증·회복 | LT-18의 조회 부분 |

LT-10, 13~15, 19~20은 아직 이 스크립트에 구현하지 않았습니다. 실제 서버의 타이머 동작, 재매칭과 패널티, 장애 복구, 다자간 교환을 검증하려면 별도 준비와 판정 기준이 필요합니다. LT-07은 반환 코드만 확인하므로 데이터베이스에 세션 중복이나 좌석 중복이 없는지 별도 점검해야 합니다. LT-08은 세션 완료를 확인하지만 교환 전후 좌석을 직접 대조하지는 않습니다.

## 쓰기 프로필: 전용 테스트 DB에서만

`lt03`, `lt04`, `lt06`, `lt07`, `lt08`은 DB 상태를 변경합니다. `course_change_loadtest`에 `tools/db/prepare_dataset.py`로 10,000명 합성 학생과 교환 요청을 적재하고, 이 DB로 연결된 백엔드를 실행한 뒤 사용하세요. 스크립트는 합성 학생의 수강 6건과 기존 요청을 확인하지만 서버의 DB 이름을 API만으로 증명할 수 없으므로 연결 설정도 직접 확인해야 합니다.

```bash
k6 run -e PROFILE=lt03 -e ALLOW_WRITES=1 -e BASE_URL=http://127.0.0.1:8080 -e WRITE_VUS=20 -e WRITE_ITERATIONS=100 scripts/k6/scenarios.js
k6 run -e PROFILE=lt04 -e ALLOW_WRITES=1 -e BASE_URL=http://127.0.0.1:8080 scripts/k6/scenarios.js
k6 run -e PROFILE=lt06 -e ALLOW_WRITES=1 -e BASE_URL=http://127.0.0.1:8080 -e WRITE_ITERATIONS=100 scripts/k6/scenarios.js
k6 run -e PROFILE=lt07 -e ALLOW_WRITES=1 -e BASE_URL=http://127.0.0.1:8080 scripts/k6/scenarios.js
k6 run -e PROFILE=lt08 -e ALLOW_WRITES=1 -e BASE_URL=http://127.0.0.1:8080 scripts/k6/scenarios.js
```

`WRITE_VUS` 기본값은 20, `WRITE_ITERATIONS`는 100, `WRITE_MAX_DURATION`은 10분입니다. `lt03`은 최대 5,000명, `lt06`은 최대 5,000쌍을 사용합니다. `lt04`, `lt07`, `lt08`은 첫 번째 합성 학생/쌍을 사용합니다. 프로필 실행 후 요청이나 세션이 남고 재실행 결과가 달라질 수 있으므로 각 쓰기 테스트 전에 전용 DB를 초기 상태로 다시 준비하세요. `lt05`도 합성 대기 요청이 있는 DB에서 실행해야 합니다.

쓰기 프로필은 의도한 409 응답이 있어 `http_req_failed` 종료 임계값을 두지 않고 `checks`로 판정합니다. 409 비율과 오류 코드를 함께 살펴보세요. 시간 목표와 최대 허용 오류율은 프로젝트 기준이 정해지면 별도로 설정해야 합니다.

### LT-07 락 설정 비교

락 없음과 비관적 락을 비교할 때는 각각 초기 상태의 전용 테스트 DB에서 같은 `WRITE_VUS`로 `lt07`을 실행하세요. 실행 결과의 `conflict_409` 값이 `EXCHANGE_CONFLICT` 코드로 거절된 **횟수**입니다. `REQUEST_NOT_MODIFIABLE` 같은 다른 409 응답은 이 값에 포함되지 않습니다. 충돌이 없으면 `conflict_409`은 0으로 표시됩니다.

`http_req_duration`의 `p(95)`는 실행 중 발생한 **모든 HTTP 요청**의 95백분위 응답시간입니다. 여기에는 로그인과 사전 확인 요청도 포함되므로 두 실행에서 같은 조건으로 비교하세요. `lt07`의 `checks` 성공률은 예상된 409도 성공으로 처리하므로 `conflict_409` 횟수를 대신하지 않습니다.
