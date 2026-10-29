# NCP 배포 가이드 (서버에 처음 한 번만 하는 설정)

## 1. API 서버 2대 (Private Subnet) — 인프라 담당
```bash
# course-api-01/02는 Rocky Linux 9.8. NAT Gateway 경로가 필요합니다.
sudo dnf install -y java-17-openjdk-headless curl

# 실행 계정, 폴더
sudo useradd -r -s /usr/sbin/nologin course
sudo mkdir -p /opt/course-change && sudo chown root:course /opt/course-change && sudo chmod 775 /opt/course-change

# 환경변수 파일 (.env.example 참고, 값은 실제로 채우기)
sudo vi /etc/course-change.env
sudo chmod 600 /etc/course-change.env     # root만 읽기 (systemd는 root로 읽어서 전달)

# 서비스 등록
sudo cp course-change.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable course-change
```
첫 배포는 한 대만 먼저 띄워서 Flyway가 테이블·시드 데이터를 만들게 한 뒤 두 번째 서버를 띄우세요.

## 2. Vue 서버 (Public Subnet)
현재 `course-vue-01`은 `/etc/nginx/nginx.conf`에 정적 파일 경로 `/var/www/vue`, `/healthz`, LB 출발지 제한을 설정해 사용합니다. 설정을 바꾸기 전 `sudo nginx -T`로 실제 로드된 구성을 확인하세요. 아래는 새 서버를 처음 준비할 때의 예시입니다. 기본 `server` 블록이 남아 있다면 `conf.d/vue.conf`가 선택되지 않을 수 있습니다.
```bash
# Vue 서버가 Rocky Linux일 때
sudo dnf install -y nginx
# Ubuntu라면: sudo apt-get update && sudo apt-get install -y nginx
sudo mkdir -p /var/www/vue
sudo cp nginx-vue.conf /etc/nginx/conf.d/vue.conf
sudo nginx -t && sudo systemctl reload nginx
```
Vue 빌드(dist)를 `/var/www/vue`에 올리는 건 프론트 레포의 배포 워크플로가 맡는다.

## LB 및 ACG 연결 확인
- Public `webserver-lb` HTTP 80 리스너: `/api/*` → `course-api-public-tg`, 기본 규칙 → Vue 서버를 등록한 `webserver-tg`.
- `course-api-public-tg`: `course-api-01/02`의 8080, 헬스체크 `/actuator/health`.
- `webserver-tg`: Vue 서버의 80, 헬스체크 `/healthz`. 두 대상이 모두 정상 상태가 된 뒤 경로별 요청을 확인합니다.
- API ACG: 8080은 Public LB 서브넷 또는 LB 접근 소스에서, 22는 Vue 서버 ACG에서 허용합니다. Vue 서버 ACG는 LB의 80과 배포용 SSH 22를 허용합니다.
- DB ACG: 신규 API ACG의 3306 접근을 허용합니다. 기존 서버가 DB를 사용 중이면 기존 접근 규칙은 전환이 끝날 때까지 유지합니다.
- Vue 서버는 키 로그인을 확인한 뒤 비밀번호 SSH 로그인을 껐습니다. Vue ACG 아웃바운드, API ACG 인바운드, 공통 web-nacl 인바운드에 Vue → API TCP 22 허용 규칙이 적용됐습니다. Vue 경유로 두 API 서버 SSH 접속과 각 서버의 `/actuator/health` 200 응답을 확인했습니다.
- 도메인이 없어 현재 LB는 HTTP 80만 사용합니다. HTTPS/Certificate Manager는 도메인 확보 후 확장합니다. 모니터링은 Cloud Insight를 사용합니다.

## 3. SSH 배포 키 (배포 전용)
```bash
ssh-keygen -t ed25519 -f bastion_deploy_key -N "" -C "github-actions-bastion"
ssh-keygen -t ed25519 -f api_deploy_key -N "" -C "github-actions-api"
# bastion_deploy_key.pub → Vue 서버의 ~/.ssh/authorized_keys
# api_deploy_key.pub → API 서버 두 대의 ~/.ssh/authorized_keys
# 각 개인키 전체 → GitHub Secret BASTION_SSH_PRIVATE_KEY, API_SSH_PRIVATE_KEY
```
- 현재 Vue용 `.local/course-change-vue-deploy`와 API용 `.local/course-change-api-deploy`를 따로 사용합니다. 두 키는 Git에서 무시되며, 각 서버에 공개키가 등록됐고 접속을 확인했습니다.
- Vue 서버: ACG 22번 전체 허용, 키 로그인만 허용 (`PasswordAuthentication no`).
- API 서버: ACG 22번은 Vue 서버 ACG에서만 허용. 운영 서버의 기존 접근 경로는 배포 검증 전까지 유지합니다.

## 4. GitHub Secrets (레포 Settings → Secrets and variables → Actions)
| 이름 | 값 |
|---|---|
| `BASTION_SSH_PRIVATE_KEY` | Vue 서버 접속용 개인키 전체 (`-----BEGIN` ~ `END` 포함) |
| `API_SSH_PRIVATE_KEY` | API 서버 접속용 개인키 전체 (`-----BEGIN` ~ `END` 포함) |
| `BASTION_HOST` | Vue 서버 공인 IP (`211.233.199.89`) |
| `API_HOSTS` | 고정 API 서버 사설 IP를 공백으로 구분 (`10.0.5.6 10.0.5.7`). 배포 순서 우선순위 + Target Group 조회 실패 시 대체 목록 |
| `SSH_USER` | 세 서버에서 접속을 확인한 `root` |

위 5개 비밀값은 `ncp-03/sugang_exchange_be` 저장소에 등록했습니다. 현재 기본 브랜치는 `main`입니다 (10/29 `wip/day22`에서 이름 변경). `.github/workflows/deploy.yml`의 수동 실행 `deploy=false`에서 Vue 경유 두 API SSH 접속을 확인했습니다. `main`의 CI 성공 뒤에는 두 API 서버에 순차 자동 배포됩니다. 수동 실행은 Actions → Deploy → Run workflow에서 `deploy=true`를 선택하면 배포하고, 기본값 `false`에서는 SSH만 점검합니다.

## 4-1. 셀프호스티드 러너 (22번 닫기)
Vue 서버에 GitHub Actions 러너를 설치하면 배포에 인바운드 SSH가 필요 없다. 설치·전환 순서는 [runner/README.md](runner/README.md). 저장소 변수 `DEPLOY_VIA_RUNNER=true`일 때만 러너를 쓰고, 없으면 위 SSH 방식 그대로다.

## 5. 시연 DB 고정 (10/29 추가)
- 배포 워크플로는 배포 전에 두 API 서버의 `/etc/course-change.env` `DB_URL`을 읽어 DB 이름이 `course_change`(시연 DB)인지 확인한다. 아니면 **배포를 멈춘다.**
- 부하테스트 때문에 `course_change_loadtest`로 바꿨다면, 끝난 뒤 반드시 `DB_URL`을 `.../course_change?...`로 되돌리고 `systemctl restart course-change`.
- 부하테스트 중에 꼭 배포해야 하면: Actions → Deploy → Run workflow에서 `allow_loadtest_db` 체크.
- 시연 데이터 초기화: Actions → Deploy → Run workflow에서 `reset_demo` 체크 (`deploy`를 끄면 초기화만 한다). API 01에서 `tools/db/seed_demo.sql`을 `course_change`에만 실행한다. push 자동 배포는 데이터를 건드리지 않는다.
- 오토스케일링 서버 이미지도 `DB_URL`이 `course_change`인 상태에서 만들어야 한다.

## 5-1. 오토스케일링 대응 배포 (10/29 추가)
ASG가 띄운 서버가 옛 이미지의 jar로 LB에 붙어서 1/3 확률로 404가 났던 문제를 막는다. 두 가지로 해결한다.

```
배포 시:  Actions ─┬─ NCP API getTargetList → Target Group의 서버 전부(01, 02, ASG…)에 한 대씩 배포
                   └─ Vue 서버 /var/www/artifacts/course-change/app.jar(+sha256)에 최신 jar 게시
부팅 시:  ASG 새 서버 ─ course-change 시작 직전(ExecStartPre) ─▶ Vue:8008에서 해시 비교 → 다르면 받아서 교체
```

### 한 번만 하는 설정
1. **NCP API 키** (조회 전용 서브 계정 권장: Sub Account → Load Balancer 조회 권한만)
   - 백엔드 레포 Secrets: `NCP_ACCESS_KEY`, `NCP_SECRET_KEY`
   - 백엔드 레포 **Variables**: `NCP_TARGET_GROUP_NO` = API Target Group 번호(숫자, 콘솔 Target Group 상세에 있음)
   - 없으면 예전처럼 `API_HOSTS`에만 배포하고 경고만 남긴다.
2. **Vue 서버 아티팩트 포트** (root, 러너를 설치했다면 그 뒤에 실행 → 폴더 소유자가 `gha`가 됨)
   ```bash
   ssh ncp-vue 'bash -s' < deploy/setup-vue-artifacts.sh
   ```
3. **ACG**
   - Vue 서버 인바운드: TCP **8008** ← `10.0.5.0/24`(API 서브넷)만
   - API 서버(ASG 포함) 아웃바운드: TCP 8008 → `10.0.1.8` (아웃바운드 전체 허용이면 그대로)
4. 위를 마친 뒤 **배포 1회** (Actions → Deploy → `deploy` 체크). 모든 API 서버에 fetch 스크립트와 systemd 드롭인
   (`/etc/systemd/system/course-change.service.d/fetch-jar.conf`)이 설치된다.
5. **ASG 이미지를 한 번만 다시 만든다** (4번 이후의 API 02 기준). 이후로는 이미지가 낡아도 부팅 때 최신 jar를 받으므로 다시 만들 필요 없다.

### 동작 규칙
- 배포 순서는 `API_HOSTS`에 있는 서버 먼저(api1 = course-api-01, 만료 스케줄러 ON, 시드 초기화 실행), 나머지는 IP 순.
- DB 확인(시연 DB `course_change`)도 Target Group 서버 **전부**에 한다. 부하테스트로 DB를 바꿀 땐 ASG 서버도 같이 바꾸고 되돌릴 것.
- 한 대라도 헬스체크에 실패하면 Vue 아티팩트와 이미 바꾼 서버까지 전부 이전 jar로 되돌린다(롤백 재시작 때는 `.skip-fetch`로 다시 받지 않음).
- fetch 스크립트는 Vue에 못 붙거나 해시가 안 맞으면 **기존 jar로 그냥 시작**한다(서비스 시작을 막지 않음). 로그: `journalctl -u course-change | grep fetch-latest-jar`
- 만료 스케줄러(`APP_MATCHING_EXPIRE_SCHEDULER`)는 course-api-01만 `true`. ASG 이미지는 API 02(false) 기준으로 만든다.
- 배포 도중 ASG가 서버를 줄이면 그 서버 SSH가 실패해 배포가 롤백된다 → 다시 실행.

## 6. 문제 확인
- `bastion ok`가 안 찍힘 → Vue 서버 22번 ACG 또는 키 불일치
- `api1` 접속 실패 → API 서버 22번이 Vue 서버 ACG에서 열렸는지, authorized_keys에 공개키가 있는지
- `health FAILED` → `journalctl -u course-change -n 100` 로 로그 확인 (DB 접속·환경변수 오류가 대부분)
