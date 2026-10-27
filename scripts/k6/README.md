# k6 조회 시나리오

LT-01~LT-20 테스트 목록 중 현재 백엔드(`wip/day22`)에 구현된 GET API로 실행할 수 있는 부분입니다. 등록·교환·매칭·세션 API는 아직 없으므로 이 스크립트는 데이터를 변경하지 않습니다.

## 실행

백엔드와 MySQL을 실행하고 [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/)를 설치한 뒤 저장소 루트에서 실행합니다.

```bash
k6 run scripts/k6/scenarios.js
k6 run -e PROFILE=lt01 -e BROWSE_VUS=5 -e DURATION=1m scripts/k6/scenarios.js
k6 run -e PROFILE=lt02 -e HOT_OFFERING_ID=9 -e HOT_VUS=20 -e DURATION=1m scripts/k6/scenarios.js
k6 run -e PROFILE=lt09 -e STUDENT_ID=1 -e STUDENT_VUS=2 scripts/k6/scenarios.js
k6 run -e PROFILE=lt12 scripts/k6/scenarios.js
k6 run -e PROFILE=lt16 -e MIXED_VUS=5 -e DURATION=30m scripts/k6/scenarios.js
k6 run -e PROFILE=lt17 -e STEP_VUS=20 -e STEP_DURATION=1m scripts/k6/scenarios.js
k6 run -e PROFILE=lt18 -e BASE_URL=http://SERVER_IP:8080 scripts/k6/scenarios.js
```

## MobaXterm에서 Cloud Insight 그래프 확인

`scenarios.js`는 k6 스크립트입니다. `scripts/cloud-insight-quick-load.sh`도 이제 k6를 실행하는 래퍼이며, `curl` 부하 생성은 하지 않습니다. MobaXterm 로컬 터미널에서는 저장소 루트에서 다음 명령을 사용합니다. 래퍼는 PATH의 k6를 찾고, 없으면 Windows 설치 경로 `/drives/c/Program Files/k6/k6.exe`를 확인합니다.

```bash
bash scripts/cloud-insight-quick-load.sh http://SERVER_IP:8080
```

SSH 터미널에서 실행한다면 `cloud-insight-quick-load.sh`와 `k6/scenarios.js`를 같은 디렉터리 구조로 옮기고 k6를 설치해야 합니다. `scenarios.js` 하나만 옮긴 경우에는 `k6 run -e PROFILE=lt18 -e BASE_URL=http://127.0.0.1:8080 scenarios.js`로 실행합니다.

`lt18`의 기본 설정은 1 VU 90초 → 5초 증가 → 32 VU 180초 → 5초 감소 → 1 VU 90초입니다. 실행 시간은 약 6분 10초이며 Ctrl+C로 중단할 수 있습니다. Cloud Insight는 1분 주기로 지표를 수집하므로 [Min1 집계](https://guide.ncloud-docs.com/docs/cloudinsight-troubleshoot-event)에서 구간을 비교합니다. 같은 서버에서 부하를 생성하면 k6 프로세스의 자원 사용도 그 서버 지표에 포함됩니다.

`BASE_URL` 기본값은 `http://localhost:8080`입니다. 다른 서버에서는 `-e BASE_URL=http://서버주소:8080`을 추가합니다. `THINK_TIME`은 반복 사이 대기 초이며 기본값 1입니다. `load`는 `lt12`의 별칭입니다. 공유 서버에 부하를 줄 때는 VU 수와 시간을 운영 담당자와 맞추세요.

| 프로필 | 현재 실행되는 요청 | 범위 |
|---|---|---|
| `smoke` | 구현된 GET API, 오류 응답 | 기본 동작 확인 |
| `lt01` | 강의 목록·키워드 검색 반복 | LT-01 |
| `lt02` | 지정한 분반 상세 반복 | LT-02의 동시 조회 부분. 9번은 샘플 데이터에서 정원이 찬 분반이며 인기 순위 정보는 없습니다. |
| `lt09` | 학생 정보·수강 목록 반복 | LT-09의 현재 상태 관찰 부분 |
| `lt12` | 목록·검색, 지정 분반 상세, 학생 조회를 동시 반복 | LT-12의 조회 부분 |
| `lt16` | 세 조회 흐름을 섞어 장시간 반복 | LT-16의 조회 부분. 기본 30분 |
| `lt17` | 조회 VU를 4단계로 증가·감소 | LT-17의 조회 처리량 탐색 부분 |
| `lt18` | 조회 VU를 기준→폭증→기준으로 변경 | LT-18의 조회 폭증·회복 관찰 부분 |

`lt09`는 수강 과목 학점 합계, 프로필 학점, 최대 학점, 기존 시간표의 겹침 여부를 확인합니다. 신규 수강이나 교환 요청이 거절되는지 검사하지는 못합니다. `lt17`과 `lt18`은 부하 패턴을 실행하며, 최대 처리량이나 회복 시간의 합격 기준은 아직 정해지지 않아 결과 지표를 보고 판단해야 합니다.

`smoke`는 검증 성공률 100%를 요구합니다. 부하 프로필은 검증 성공률 99% 초과, HTTP 실패율 1% 미만을 요구합니다. 응답 시간 목표는 설정하지 않았습니다. `smoke`의 의도적인 400·401·404 응답은 k6의 기본 `http_req_failed`에 실패로 집계될 수 있으므로 `checks`와 종료 코드로 판단합니다.

## LT 목록 대비 범위

| 항목 | 현재 상태 | 이유 |
|---|---|---|
| LT-01 강의 목록 및 검색 | 실행 가능 | 목록·검색을 반복 조회 |
| LT-02 인기 강의 동시 조회 | 일부 | 지정 분반에 집중 조회, 인기 순위 데이터 없음 |
| LT-03~08 등록·대기열·자동 매칭·동시 확정 | 미구현 | 해당 등록·매칭 API 없음 |
| LT-09 학점·시간표 검증 | 일부 | 기존 수강 상태의 합계·시간표만 검사 |
| LT-10~11 세션 만료·폴링·알림 | 미구현 | 해당 API 없음 |
| LT-12 수강신청 종합 피크 부하 | 일부 | 조회 API만 함께 실행 |
| LT-13~15 동의·거절·재매칭·패널티 | 미구현 | 해당 API 없음 |
| LT-16~18 지속·단계·순간 부하 | 일부 | 조회 API에만 적용 |
| LT-19 서버 장애 및 자동 복구 | 미구현 | 장애 주입·복구 검증 없음 |
| LT-20 다자간 순환 교환 | 미구현 | 교환 API 없음 |

샘플 분반 9의 시간표 기대값은 `화목 15:00-16:15`입니다. 기존 GitHub Actions에서 9시간 차이가 나는 실패가 확인되었으므로 백엔드가 수정되기 전에는 `smoke`가 실패할 수 있습니다.
