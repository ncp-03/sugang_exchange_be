# 셀프호스티드 러너 전환 기록 (Deploy 실행 로그 발췌)

GitHub Actions 로그는 보관 기간이 지나면 지워지므로, 전환 전후 실행의 핵심 줄을 옮겨 둡니다.
시각은 UTC입니다 (KST = UTC+9).

## 전환 직전 — GitHub 호스팅 러너 → Vue 서버 SSH 중계

[Run 40998423568 · API 서버 배포](https://github.com/ncp-03/sugang_exchange_be/actions/runs/40998423568/job/123876903353)

```
2026-10-30T03:37:43Z 배포 경로: GitHub 러너 → Vue(SSH 중계) → API
2026-10-30T03:37:49Z api1 (10.0.5.6) ok: course-api-01
2026-10-30T03:37:53Z api2 (10.0.5.7) ok: course-api-02
2026-10-30T03:37:57Z api3 (10.0.5.8) ok: apipub-b9x4s743
2026-10-30T03:38:01Z api4 (10.0.5.9) ok: apipub-aik8gckq
```

## 전환 후 첫 실행 — Vue 서버 안의 셀프호스티드 러너

[Run 40999494671 · API 서버 배포](https://github.com/ncp-03/sugang_exchange_be/actions/runs/40999494671/job/123880320639)

```
2026-10-30T03:49:29Z Runner name: 'course-vue-01-be'
2026-10-30T03:49:29Z Machine name: 'course-vue-01'
2026-10-30T03:49:34Z 배포 경로: 셀프호스티드 러너(Vue 서버) → API 사설 IP
2026-10-30T03:49:34Z api1 (10.0.5.6) ok: course-api-01
2026-10-30T03:49:34Z api2 (10.0.5.7) ok: course-api-02
2026-10-30T03:49:35Z api3 (10.0.5.8) ok: apipub-b9x4s743
2026-10-30T03:49:35Z api4 (10.0.5.9) ok: apipub-aik8gckq
```

이후 10/30 05:44 UTC까지 Deploy 9회가 모두 셀프호스티드 러너에서 성공했습니다.
API 서버 4대(오토스케일링으로 늘어난 2대 포함)에 사설 IP로 접속했고, 외부에서 Vue 서버 22번으로 들어오는 SSH는 쓰지 않았습니다.
