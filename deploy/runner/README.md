# 셀프호스티드 러너로 배포하기 (Vue 서버 SSH 22번 닫기)

## 왜 바꾸나
지금은 GitHub 호스팅 러너가 **밖에서 Vue 서버 22번으로 SSH 접속**해서 배포한다. 러너 IP가 매번 바뀌어서 ACG에 22번을 `0.0.0.0/0`으로 열어 둘 수밖에 없다.

셀프호스티드 러너는 Vue 서버 **안에서** 돌면서 GitHub에 **나가는 연결(443)** 로 작업을 받아 온다. 그래서 들어오는 22번이 필요 없다.

```
[기존]  GitHub 러너 ──SSH 22(인바운드, 전체 공개)──▶ Vue 서버 ──SSH──▶ API 01/02
[변경]  GitHub ◀──HTTPS 443(아웃바운드)── 러너(Vue 서버 안) ──SSH(사설망)──▶ API 01/02
                                                         └─ /var/www/vue 직접 교체
```

- 빌드(jar, Vue dist)는 계속 GitHub 호스팅 러너에서 하고, **배포 단계만** 셀프호스티드 러너가 맡는다.
- 백엔드·프론트 워크플로 모두 저장소 변수 `DEPLOY_VIA_RUNNER=true`일 때만 러너를 쓴다. 변수가 없으면 기존 SSH 방식 그대로라서, 러너 준비 전에 이 코드가 먼저 올라가도 아무것도 안 바뀐다.

## 1. 러너 설치 (Vue 서버 course-vue-01, 인프라 담당)

### 1-1. 러너 전용 계정
```bash
sudo useradd -m -s /bin/bash gha
# 프론트 배포: 러너가 /var/www/vue 를 직접 바꿀 수 있게 소유자 변경 (nginx는 읽기만 하면 됨)
sudo chown -R gha:gha /var/www/vue
sudo chmod 755 /var/www/vue
```

### 1-2. 러너 등록
GitHub에서 등록 명령(토큰 포함)을 받는다. **둘 중 하나**:
- **조직 러너(추천)**: `ncp-03` 조직 → Settings → Actions → Runners → New runner → Linux x64. 러너 하나로 백엔드·프론트 레포 둘 다 쓴다. (조직 Owner 권한 필요. Runner groups에서 두 레포가 허용돼 있는지 확인)
- **레포 러너**: 조직 권한이 없으면 각 레포 → Settings → Actions → Runners → New self-hosted runner. 레포마다 폴더를 따로 만들어 2개 설치 (`~/actions-runner-be`, `~/actions-runner-fe`).

화면에 나오는 명령을 `gha` 계정으로 실행하되, `config.sh`에 **라벨 `ncp-vue`** 를 꼭 넣는다.
```bash
sudo -iu gha
mkdir actions-runner && cd actions-runner
# ↓ GitHub 화면의 curl / tar 명령 그대로 (버전은 화면 기준)
# curl -o actions-runner-linux-x64-<버전>.tar.gz -L https://github.com/actions/runner/releases/download/v<버전>/actions-runner-linux-x64-<버전>.tar.gz
# tar xzf ./actions-runner-linux-x64-<버전>.tar.gz
./config.sh --url https://github.com/ncp-03 --token <화면의 토큰> \
  --name course-vue-01 --labels ncp-vue --unattended
exit
```
(레포 러너면 `--url https://github.com/ncp-03/<레포 이름>`)

### 1-3. 의존성 + 서비스 등록 (root)
```bash
cd /home/gha/actions-runner
sudo ./bin/installdependencies.sh
sudo ./svc.sh install gha      # gha 계정으로 실행되는 서비스
sudo ./svc.sh start
sudo ./svc.sh status           # active (running)
```
GitHub Runners 화면에서 `course-vue-01`이 **Idle**(초록)이면 성공.

### 1-4. Vue 서버 ACG **아웃바운드**
러너는 GitHub(`github.com`, `api.github.com`, `*.actions.githubusercontent.com`, 아티팩트 저장소 등)에 HTTPS로 나가야 한다.
- Vue 서버 ACG 아웃바운드에 **TCP 443 → 0.0.0.0/0** 허용 (도메인 단위 제한이 안 되므로 443 전체)
- Vue → API **TCP 22** 아웃바운드 규칙은 이미 있음 (유지)

## 2. 전환 (명주)
1. 두 레포 각각 Settings → Secrets and variables → Actions → **Variables** 탭 → `DEPLOY_VIA_RUNNER` = `true`
   (조직 변수로 한 번에 만들어도 됨)
2. 백엔드: Actions → Deploy → Run workflow, `deploy` **끈 채로** 실행 → "배포 경로: 셀프호스티드 러너", `api1 ok`, `api2 ok`, DB 확인 통과면 OK
3. 프론트: Actions → Deploy Frontend → Run workflow → `frontend OK`
4. 둘 다 성공한 뒤에만 **Vue 서버 ACG 인바운드 22번**: `0.0.0.0/0` 삭제 → 팀원 공인 IP `/32`만 허용 (또는 아예 삭제)
   - 22번을 닫으면 내 PC의 `ssh ncp-vue`, `ssh ncp-api1`(Vue 경유)도 막힌다. 시연 시드 초기화는 Actions의 `reset_demo`로 한다.
5. GitHub Secrets 중 `BASTION_SSH_PRIVATE_KEY`, `BASTION_HOST`(백엔드), `VUE_SSH_PRIVATE_KEY`, `VUE_HOST`(프론트)는 셀프호스티드 모드에선 안 쓴다. 되돌릴 일이 없을 때 지운다.

## 되돌리기
러너가 죽었거나 문제가 생기면: `DEPLOY_VIA_RUNNER` 변수를 지우거나 `false`로 → 기존 SSH 방식으로 돌아간다 (단, ACG 22번을 다시 열어야 함).

## 주의
- `DEPLOY_VIA_RUNNER=true`인데 러너가 꺼져 있으면 배포 작업이 **대기(Queued)** 상태로 멈춘다. `sudo ./svc.sh status`로 확인.
- 러너는 Vue 서버에서 `gha` 계정으로 돈다(root 아님). API 서버 SSH 키는 작업 중에만 `$RUNNER_TEMP`에 쓰고 끝나면 지운다.
- ⚠️ **두 레포가 public이다.** public 레포의 셀프호스티드 러너는 외부인이 포크 PR에서 워크플로를 고쳐 `runs-on: [self-hosted, ncp-vue]`로 우리 서버에서 코드를 돌릴 위험이 있다(GitHub도 권장하지 않음). 전환 전에 둘 중 하나는 꼭 한다.
  - 레포를 **private**로 바꾸기 (가장 확실)
  - 또는 두 레포 Settings → Actions → General → *Fork pull request workflows* → **Require approval for all external contributors** (외부 PR은 우리가 승인해야만 실행)
- Rocky Linux의 SELinux 때문에 nginx가 403을 내면: `sudo restorecon -R /var/www/vue` (배포 스크립트도 가능하면 실행함)
