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
| `API_HOSTS` | 두 API 서버 사설 IP를 공백으로 구분 (`10.0.5.6 10.0.5.7`) |
| `SSH_USER` | 세 서버에서 접속을 확인한 `root` |

위 5개 비밀값은 `ncp-03/sugang_exchange_be` 저장소에 등록했습니다. 현재 기본 브랜치는 `wip/day22`입니다. `.github/workflows/deploy.yml`은 수동 실행 전용입니다. Actions → Deploy → Run workflow에서 기본값 `deploy=false`로 실행하면 Vue 경유 API SSH 접속만 점검하고, `deploy=true`로 실행하면 두 API 서버에 순차 배포합니다. SSH 검사에 성공한 뒤 자동 배포가 필요하면 `wip/day22`의 CI 성공을 조건으로 추가하세요.

## 5. 문제 확인
- `bastion ok`가 안 찍힘 → Vue 서버 22번 ACG 또는 키 불일치
- `api1` 접속 실패 → API 서버 22번이 Vue 서버 ACG에서 열렸는지, authorized_keys에 공개키가 있는지
- `health FAILED` → `journalctl -u course-change -n 100` 로 로그 확인 (DB 접속·환경변수 오류가 대부분)
