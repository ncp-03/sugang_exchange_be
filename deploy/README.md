# NCP 배포 가이드 (서버에 처음 한 번만 하는 설정)

## 1. API 서버 2대 (Private Subnet) — 인프라 담당
```bash
# Java 17 설치 (NAT Gateway 필요)
sudo apt-get update && sudo apt-get install -y openjdk-17-jre-headless curl

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
```bash
sudo apt-get install -y nginx
sudo mkdir -p /var/www/vue
sudo cp nginx-vue.conf /etc/nginx/conf.d/vue.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```
Vue 빌드(dist)를 `/var/www/vue`에 올리는 건 프론트 레포의 배포 워크플로가 맡는다.

## 3. SSH 배포 키 (배포 전용)
```bash
ssh-keygen -t ed25519 -f deploy_key -N "" -C "github-actions-deploy"
# deploy_key.pub  → Vue 서버와 API 서버 두 대의 ~/.ssh/authorized_keys 에 등록
# deploy_key(개인키 전체) → GitHub Secret SSH_PRIVATE_KEY
```
- Vue 서버: ACG 22번 전체 허용(키 로그인만, `PasswordAuthentication no`)
- API 서버: ACG 22번은 Vue 서버 ACG에서만 허용

## 4. GitHub Secrets (레포 Settings → Secrets and variables → Actions)
| 이름 | 값 |
|---|---|
| `SSH_PRIVATE_KEY` | deploy_key 파일 내용 전체 (`-----BEGIN` ~ `END` 포함) |
| `BASTION_HOST` | Vue 서버 공인 IP |
| `API_HOSTS` | API 서버 사설 IP를 공백으로 구분 (예: `10.0.2.11 10.0.2.12`) |
| `SSH_USER` | 접속 계정 (예: `root` 또는 배포용 계정) |

## 5. 문제 확인
- `bastion ok`가 안 찍힘 → Vue 서버 22번 ACG 또는 키 불일치
- `api1` 접속 실패 → API 서버 22번이 Vue 서버 ACG에서 열렸는지, authorized_keys에 공개키가 있는지
- `health FAILED` → `journalctl -u course-change -n 100` 로 로그 확인 (DB 접속·환경변수 오류가 대부분)
