# 배포 — Vercel & Railway

모노레포(폴더 하나에 frontend/backend 공존)를 플랫폼 두 곳에 나눠 배포한다.

| 대상 | 무엇이 배포를 거나 |
|---|---|
| frontend | Vercel 이 GitHub 연동으로 `main` push 때 직접 올린다 |
| backend | `.github/workflows/deploy-backend.yml` 이 verify 4잡이 `main` 에서 초록일 때 `railway up` 을 부른다 |

Railway 서비스에는 GitHub 저장소가 붙어 있지 않다(`source.repo = null`). 그래서 2026-09-18 까지 `main` 에 무엇을 올려도 배포가 안 걸렸고, 프로덕션이 엿새 전 빌드에 멈춰 `/api/jobs` 가 404 였다. 대시보드에서 저장소를 붙이는 대신 워크플로로 옮긴 이유는 **검증을 배포의 조건으로 두기 위해서**다 — 저장소를 붙이면 CI 가 빨간 커밋도 그대로 나간다.

워크플로는 저장소 시크릿에서 토큰을 읽는다. 둘 중 아무거나 하나면 된다.

| 시크릿 | 무슨 토큰 | 어디서 만드나 |
|---|---|---|
| `RAILWAY_TOKEN` | 프로젝트 토큰. 프로젝트와 환경이 토큰에 박혀 있다 | 프로젝트 Settings 의 토큰 화면 |
| `RAILWAY_API_TOKEN` | 계정·워크스페이스 토큰. 범위가 넓어 워크플로가 프로젝트와 환경을 따로 넘긴다 | https://railway.com/account/tokens |

둘 다 없으면 배포 잡이 그 자리에서 실패한다. 조용히 건너뛰면 낡은 배포를 모르고 지나간다.

```
GitHub(main push)
 ├─→ Vercel   : frontend/ 를 루트로 빌드·배포 (Next.js)
 └─→ Railway  : backend/  를 루트로 빌드·배포 (Spring Boot)
```

프론트 → 백엔드 연결은 Vercel 환경변수 `NEXT_PUBLIC_SPRING_URL`이 Railway 배포 URL을 가리키는 방식. 도메인이 다르므로 백엔드 `WebConfig.java`의 CORS 설정이 필수다.

## Vercel (frontend)

- Next.js 개발사가 운영하는 호스팅 — Next.js 배포는 사실상 무설정
- 이 프로젝트 설정: 프로젝트 루트를 `frontend/`로 지정 (모노레포 Root Directory 설정)
- 환경변수: `NEXT_PUBLIC_SPRING_URL`, `NEXT_PUBLIC_KAKAO_JS_KEY` — Project Settings > Environment Variables에 등록. `NEXT_PUBLIC_` 접두사가 붙은 변수만 브라우저에 노출된다 (빌드 시점에 코드에 박제되므로 변경하면 재배포 필요)
- 대시보드: https://vercel.com/ejg93s-projects/ejgsproject
- 정식 문서:
    - Vercel 문서 홈: https://vercel.com/docs
    - 모노레포 배포: https://vercel.com/docs/monorepos
    - 환경변수: https://vercel.com/docs/environment-variables
    - Next.js 배포 가이드: https://vercel.com/docs/frameworks/nextjs

## Railway (backend)

- 컨테이너 기반 PaaS. Java/Maven 프로젝트를 감지해 자동 빌드(Nixpacks) 후 실행
- 이 프로젝트 설정: 서비스 루트를 `backend/`로 지정
- 환경변수: Railway Variables 가 relaxed binding 으로 속성에 들어간다(`DATA_GO_KR_KEY` → `data.go.kr.key`). ANTHROPIC/YOUTUBE/KAKAO/SARAMIN 키, 그리고 청약 알리미의 다섯:
    - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`, `SPRING_DATASOURCE_USERNAME` = `${{Postgres.PGUSER}}`, `SPRING_DATASOURCE_PASSWORD` = `${{Postgres.PGPASSWORD}}` — 같은 프로젝트 `Postgres` 서비스를 내부망으로 참조한다
    - `DATA_GO_KR_KEY` — 공공데이터포털 일반 인증키(Decoding)
    - `CHEONGYAK_COLLECT_TOKEN` — 수집 입구 보호 토큰. GitHub Secrets 에도 같은 값(`collect-cheongyak.yml` 이 헤더로 보낸다)
- DB: 같은 프로젝트의 `Postgres` 서비스. Flyway 가 기동 때 `db/migration` 을 적용한다. TCP Proxy(Public Access)는 꺼 둔다 — 백엔드는 내부망으로 붙는다
- 대시보드: https://railway.com/dashboard
- 정식 문서:
    - Railway 문서 홈: https://docs.railway.com/
    - 모노레포: https://docs.railway.com/guides/monorepo
    - 환경변수: https://docs.railway.com/guides/variables

## 배포 체크리스트

1. 로컬에서 `npm run build`(frontend), `mvn package`(backend) 통과 확인
2. 새 환경변수 추가 시: Vercel/Railway 양쪽 콘솔에도 등록 + 루트 `CLAUDE.md` 표 갱신
3. 백엔드 URL 변경 시: Vercel의 `NEXT_PUBLIC_SPRING_URL` 갱신 후 프론트 재배포 (빌드 시점 박제이므로)
4. CORS 오류 발생 시: `WebConfig.java`의 허용 origin에 프론트 도메인 포함됐는지 확인
5. datasource 변수 없이 배포하면 기동이 실패해 배포 확인(`/api/jobs`)이 빨갛게 된다
6. Postgres 의 TCP Proxy 가 켜져 있으면 끈다
