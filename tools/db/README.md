# DB 스키마·가상 데이터·검증

2026-10-26까지 준비한 DB 지원 코드를 공유한다. 기존 V1·V2 및 로그인 컬럼이 추가된 V3에서 동작하며, 앱의 자동 마이그레이션 경로 밖에 있는 수동 실행 도구다.

## 테이블 생성과 학생 10명 샘플

기존 백엔드의 Flyway SQL이 기준이다. 같은 SQL을 복사하거나 수정하지 않는다.

- [V1__init_schema.sql](../../src/main/resources/db/migration/V1__init_schema.sql): 학사·교환·매칭 관련 애플리케이션 테이블 11개 생성.
- [V2__seed_sample_data.sql](../../src/main/resources/db/migration/V2__seed_sample_data.sql): 가상 학생 10명, 과목 13개, 분반 21개, 수업 시간 40개, 수강 내역 45개.
- [V3__add_student_password.sql](../../src/main/resources/db/migration/V3__add_student_password.sql): 로그인 해시 컬럼과 기존 가상 학생의 초기 로그인 정보.
- [verify_demo.sql](verify_demo.sql): `course_change`의 초기 샘플 수, 시간표 충돌, 최대 학점, 정원 검증. 조회만 수행한다.

기본 시연 DB `course_change`와 시험 DB `course_change_loadtest`는 구분한다. 아래 생성기는 시험 DB만 대상으로 하며 기본 시연 DB에 대량 데이터를 넣지 않는다.

## 파일

| 파일 | 용도 |
| --- | --- |
| `prepare_dataset.py` | 재현 가능한 CSV 생성, 시험 DB 입력, 조회 검증 |
| `requirements.txt` | DB 연결에만 사용하는 PyMySQL |
| `test_prepare_dataset.py` | CSV 재현성, V2·V3 호환성, 로그인 해시 회귀 검사 |
| `verify_demo.sql` | 학생 10명인 기본 시연 DB 조회 검증 |
| `verify_loadtest.sql` | 시험 DB 데이터 수와 7가지 무결성 조건 조회 |
| `dataset-manifest.expected.json` | 원래 생성한 CSV의 행 수·SHA-256 기준 |
| `dataset-verification.reference.json` | 2026-10-27에 수행한 시험 DB 검증 기록. 현재 DB 상태를 보증하는 파일은 아님 |

CSV·실제 접속 정보·개인키·비밀번호는 포함하지 않는다. CSV는 아래 명령으로 생성하며 `generated/`는 Git에서 제외한다. 생성만 할 때는 Python 3.9 이상과 표준 라이브러리만 필요하다.

## 1. CSV 생성

저장소 최상위에서 실행한다.

```sh
python tools/db/prepare_dataset.py generate
```

`tools/db/generated/dataset/`에 CSV 8개, 상위에 `dataset-manifest.json`이 생긴다. 날짜·식별자·생성 규칙이 고정되어 있어 같은 SHA-256으로 재생성된다. `--output-dir <폴더>`로 출력 위치를 지정할 수도 있다.

| 테이블 | 추가 가상 데이터 | V2 포함 예상 합계 |
| --- | ---: | ---: |
| students | 10,000 | 10,010 |
| courses | 1,200 | 1,213 |
| course_offerings | 2,400 | 2,421 |
| class_times | 4,800 | 4,840 |
| enrollments | 60,000 | 60,045 |
| wishlists | 30,000 | 30,000 |
| exchange_requests | 10,000 | 10,000 |
| exchange_preferences | 10,000 | 10,000 |

총 128,400행을 추가한다. 가상 ID는 100001부터 시작하며, 학생마다 3학점 강의 6개(18학점)를 배정한다. 교환 요청은 같은 과목의 다른 분반으로 서로 교환할 상대가 있도록 구성한다. 대표 분반 100001은 25명 정원이 채워진 상태다. 실제 학생 정보는 사용하지 않는다.

## 2. 시험 DB 준비와 접속

이미 가상 데이터가 입력된 팀 시험 DB에는 `apply`를 다시 실행할 필요가 없다. `verify`만 실행한다.

새 시험 환경은 DB 관리자가 빈 `course_change_loadtest` 스키마와 필요한 권한을 준비하고, 기존 백엔드를 그 DB에 연결해 Flyway V1·V2·V3를 적용해야 한다. 기존 V2 시험 DB도 지원한다. 운영 앱의 DB 연결은 바꾸지 않는다. 생성기는 DB·계정 생성, 마이그레이션, 데이터 삭제를 자동 수행하지 않는다.

DB가 사설망에 있으면 서버를 통한 SSH 터널을 사용한다. 다음은 별도 터미널에서 유지하는 예시다. 실제 계정·주소로 치환한다.

```sh
ssh -N -L 13306:<DB_HOST>:3306 <SSH_USER>@<SERVER_IP>
```

```sh
python -m pip install -r tools/db/requirements.txt
```

`--host`, `--port`, `--user` 또는 환경변수 `DB_HOST`, `DB_PORT`, `DB_USERNAME`을 사용한다. 비밀번호는 실행할 때 숨김 입력으로 받는다. 이미 `DB_PASSWORD` 환경변수가 있다면 그 값을 사용한다. 비밀번호를 명령 인수나 소스에 적지 않는다.

## 3. 입력과 검증

깨끗한 V2 샘플 상태의 시험 DB에 처음 입력할 때만 실행한다.

```sh
python tools/db/prepare_dataset.py apply --host 127.0.0.1 --port 13306 --user <DB_USER>
```

8개 대상 테이블이 예상 V2 초기 행 수와 다르면 중복 입력을 거부한다. CSV 해시를 확인하고 한 트랜잭션으로 입력하며, 오류 시 롤백한다. 기존 데이터를 지우거나 덮어쓰지 않는다.

V3 DB에는 학생을 입력할 때 로그인 해시도 함께 넣는다. V3와 같은 가상 데이터 규칙으로 초기 비밀번호는 해당 가상 학번이며, CSV 내용과 기존 해시는 유지한다. V2 DB에는 아직 없는 `password_hash` 컬럼을 사용하지 않는다.

```sh
python tools/db/prepare_dataset.py verify --host 127.0.0.1 --port 13306 --user <DB_USER>
```

검증은 데이터 수, Flyway V1·V2 또는 V1·V2·V3 성공 여부, 대표 학생의 수강 6개와 대표 분반의 25명을 확인하고 다음 위반 수가 모두 0인지 확인한다. V3에서는 가상 학생의 로그인 해시 누락·불일치도 확인한다. 아직 지원하지 않는 마이그레이션이 있으면 입력·검증을 중단한다.

1. 학생 시간표 충돌
2. 최대 신청 학점 초과
3. 분반 정원 초과
4. 교환 요청과 수강 내역의 학생 불일치
5. 동일 과목·다른 분반 조건 불일치
6. 서로 교환할 상대 부재
7. 이미 수강한 분반의 찜

결과는 `generated/dataset-verification.json`에 저장한다. MySQL 클라이언트에서 직접 확인하려면 로그인 후 다음 파일을 사용한다.

```sql
SOURCE tools/db/verify_demo.sql;
SOURCE tools/db/verify_loadtest.sql;
```

`verify_demo.sql`의 행 수 비교는 초기 시연 샘플 기준이다. 서비스 사용으로 데이터가 바뀌었다면 기대값과 다른 이유를 먼저 확인한다. V3 이후 마이그레이션이 추가되면 입력·검증 코드의 호환성도 확인해야 한다.

## 회귀 검사와 CI

```sh
python -m unittest discover -s tools/db -p 'test_*.py' -v
```

`DB fixtures` 워크플로는 별도의 MySQL 8.0 서비스에서 실제 Flyway를 V2·V3까지 각각 적용하고 CSV 생성·입력·검증 및 중복 입력 거부를 검사한다. V3에서는 첫 번째와 마지막 가상 학생이 실제 로그인 API로 토큰을 받는지도 확인한다. V2 시험 실행에서만 아직 없는 V3 컬럼 때문에 JPA 스키마 검사를 끄며 운영 앱 설정은 바꾸지 않는다.

## 확인한 범위

원래 데이터는 기존 NCP MySQL 8.0 시험 DB에 입력한 뒤 8개 테이블의 행 수와 7가지 조건을 검증했다. 공유용 생성기는 원래 CSV 8개의 SHA-256과 일치하는지 별도로 확인했다. 데이터 준비와 조회 검증을 수행한 기록이며 부하 성능 측정 결과는 아니다.
