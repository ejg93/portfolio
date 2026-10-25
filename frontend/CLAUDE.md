# frontend — Next.js 16 앱

App Router 기반 포트폴리오 화면. Vercel이 이 폴더를 루트로 잡고 `main` push마다 자동 배포한다.

이 문서는 `app/`, `components/`, `lib/`, 설정 파일을 대상으로 한다. `public/` 아래 정적 보관물은 각자 다른 규칙을 따르니 루트 [CLAUDE.md](../CLAUDE.md) 구역 지도를 본다.

## 구조

```
app/          라우트별 page.tsx (App Router)
components/   AppShell.tsx — 테마 토글·사이드바를 쥔 껍데기(클라이언트)
              Sidebar.tsx — 전 페이지 공통 좌측 내비
              CaseStudy.tsx — 설계 기록 페이지 공통 카드·섹션 스타일
lib/api.ts    백엔드 베이스 URL·공개 키 상수 중앙 관리
lib/clientStore.ts  localStorage·미디어쿼리 훅(useStoredFlag·useStoredJson·useMediaQuery·useHydrated)
lib/github.ts ProjectShop 저장소 숫자·최근 커밋을 GitHub API 로 받아온다
```

새 화면은 `app/<경로>/page.tsx`로 만든다. 백엔드 주소나 공개 키를 페이지에 직접 박지 말고 `lib/api.ts`에서 import 한다.

## 라우트

| 프론트 | 호출하는 백엔드 API | 화면 내용 |
|---|---|---|
| `/` | - | 홈 |
| `/about` | - | 소개 |
| `/youtube` | `GET /api/youtube/{comments,replies}` | 유튜브 댓글 뷰어 |
| `/public-data` | `GET /api/jobs` | 채용정보 목록 + 지도. Sidebar·홈에 없고 URL 직접 접근만 된다 |
| `/cheongyak` | `GET /api/cheongyak/{notices,runs,rules}`·`POST /api/cheongyak/judge` | 청약 공고 판정(LAB). 내 조건 → 수도권 공고 카드별 1순위·2순위·불가와 근거. 판정은 서버 규칙 엔진 응답을 그대로 그린다. 조건·검색 칩은 localStorage `cheongyak-slots`(슬롯 5개)·`cheongyak-last-slot` 에만 둔다 |
| `/projectshop` | - | 별도 저장소 [ProjectShop](https://github.com/ejg93/ProjectShop) 소개. 설계 결정·작업 방식·CI 체계를 정리한 화면. 숫자와 최근 커밋은 `lib/github.ts` 가 GitHub API 로 받아온다(ISR 15분). 호출이 실패하면 `page.tsx` 의 `FALLBACK` 배열로 떨어진다 |
| `/workflow` | - | 작업 방식 소개. 세션 뼈대·강제 지점 여섯·chunkframe 템플릿. 막힐 때 찍히는 메시지는 `workflow/quotes.ts` 에 원문 그대로 둔다(줄바꿈 규칙 6의 대상이 아니다) |
| `/toolbox` | - | 폐쇄망 단일 파일 도구 소개. 카드 목록은 `public/toolbox/toolbox.html` 의 런처 배열을 빌드 때 읽어 만든다. 도구 본체는 `/tools/:path*` rewrite 로 연다 |
| `/study` | - | 학습 노트 색인. 목록은 `public/study/*.html` 의 메타에서 빌드 때 만들고 kind(배움·결정·계획)로 묶는다. 노트 추가 = HTML 한 장 + 메타 넷(description·keywords·date·kind). keywords 는 `page.tsx` 의 사전 안에서만 고른다 — 밖이면 build 가 실패한다. 본문은 `<main id="mainContent" data-title data-stages>` 안의 `<article class="doc" id data-stage>` 만 쓰고 셸(사이드바·진행률·테마·pager)은 `public/study/shell.js` 가 만든다. 절 안 스크립트 0줄 |
| (없음) | `POST /api/battle/chat` | AI 배틀 화면은 `app/_ai-battle/` 로 내려 라우트에서 뺐다. `_` 로 시작하는 폴더는 Next 가 라우팅하지 않는다. 되살리려면 폴더명에서 `_` 를 뗀다 |
| `/board` | - | 게시판. 정적 데모를 `demos.tsx`/`demoMap.tsx`로 등록 |

## Next 가 건드리는 파일

`next build`·`next dev` 는 `AGENTS.md` 에 자기 규칙 블록을 써 넣는다. 그 파일이 없으면 `CLAUDE.md` 를 대신 고친다 — 그래서 `AGENTS.md` 를 두고 블록을 그쪽으로 몰아 뒀다. 우리 규칙과 도구가 관리하는 글을 한 파일에 섞지 않는다.

`tsconfig.json` 의 include 도 빌드가 손댄다 — 새 distDir 이 생기면 한 줄 늘어난다. 그건 커밋한다.

`next-env.d.ts` 는 git 에 없다. 빌드·dev·`next typegen` 이 마지막 distDir 을 가리키게 다시 쓰는 파일이라 커밋하면 빌드마다 뒤집힌다(2026-10-15 에 뺐다). `npm run typecheck` 가 `next typegen` 으로 먼저 만들므로 새로 받은 트리에서도 tsc 가 돈다.

## 번들러

Next 16 은 Turbopack 으로 빌드한다. postcss 플러그인을 이름 문자열로 적으면 Turbopack 워커가 못 찾아 빌드가 깨진다. `postcss.config.js` 에서 `require('@tailwindcss/postcss')` 로 직접 넘겨 해석을 그 파일 기준으로 고정해 뒀다.

## 스타일

Tailwind 4 가 설치돼 있지만(설정 파일 없이 `globals.css` 의 `@import "tailwindcss"` 와 v3 preflight 호환 규칙만 쓴다), **화면 스타일은 대부분 `style={{ }}` 인라인으로 작성돼 있다.** 페이지당 인라인 수십 건, className 10건 안팎. 기존 페이지를 고칠 때는 그 페이지가 이미 쓰는 방식을 따라가고, 인라인을 Tailwind 클래스로 바꾸는 일괄 변환은 요청 없이 하지 않는다.

## 환경변수

`.env.local` + Vercel Project Settings 양쪽에 넣어야 배포본에서도 동작한다.

| 변수 | 용도 |
|---|---|
| `NEXT_PUBLIC_SPRING_URL` | 백엔드(Railway) API 베이스 URL. 없으면 `http://localhost:8080` |
| `NEXT_PUBLIC_KAKAO_JS_KEY` | Kakao Maps JS SDK를 브라우저에서 로드할 때 쓰는 공개 키 |
| `GITHUB_TOKEN` | `/projectshop` 이 ProjectShop 저장소 숫자를 받아올 때 쓴다. 없어도 동작한다. 없으면 무인증 60회/시 |

`NEXT_PUBLIC_` 접두사가 붙은 값은 번들에 그대로 실려 브라우저에 노출된다. 비밀 키는 백엔드에 둔다.

## 경로에 한글이 들어갈 때

`/` 로 시작하는 문자열 리터럴에 한글이 있으면 **그 자리에서** `encodeURI` 로 감싼다. 상수에 날것으로 담아 두고 쓰는 쪽에서 감싸면 빠뜨린다. `scripts/href-lint.js` 가 고친 줄에서 잡는다.

```ts
const SAMPLE_DIR = encodeURI('/toolbox/논리명_변환기_sample')
```

브라우저는 주소창에서 알아서 인코딩해 클릭은 되지만, 그 href 를 복사해 요청하면 404 다.

## 로컬 실행

```bash
npm install
npm run dev               # localhost:3000
```

## 검증

저장소 루트의 `bash scripts/verify.sh` 가 아래 표에서 typecheck·build·e2e 를 골라 돌린다. 표는 하나만 손으로 돌릴 때 고르는 기준이다. 아래로 갈수록 비용이 크다.

| 명령 | 무엇을 잡나 | 언제 |
|---|---|---|
| `npm run typecheck` | 타입 불일치, null 가능성, 없는 속성 접근 | `.ts`·`.tsx` 수정 후 |
| `npm run lint` | `useEffect` 의존성 누락, 이펙트 안 setState 등 실수 패턴 | 컴포넌트·훅 수정 후 |
| `npm run build` | 정적 생성·설정 오류. **lint 는 안 돈다**(Next 16) | **커밋 전 최소 1회** |
| `npm audit --omit=dev` | 배포본에 실리는 의존성의 취약점 | 의존성 추가·변경 후 |
| `npm run e2e` | 라우트 10개와 public/ 정적 HTML 10장을 크로미엄으로 열어 콘솔 에러·죽은 내부 링크·접근성 위반·폰 폭 가로 넘침 | 화면 문구·색·레이아웃을 고친 뒤 |

`verify.sh` 의 빌드는 `NEXT_DIST_DIR=.next-verify` 로 딴 폴더에 쓴다 — dev 서버가 쓰는 `.next` 를 갈아엎으면 `GET / 500` 이 난다. 손으로 돌릴 때도 dev 가 떠 있으면 그 변수를 붙인다.

`npm run build`가 Vercel이 실제로 돌리는 명령이다. 여기서 실패하면 배포도 실패한다. **빌드가 깨진 상태로 작업을 끝내지 않는다.**

Next 16 부터 `next build` 가 ESLint 를 안 돌린다. `next lint` 명령 자체도 없어졌다. 그래서 `npm run lint` 는 `eslint .` 를 직접 부르고, 설정은 flat 형식인 `eslint.config.mjs` 에 있다. `verify.sh` 와 CI 가 build 와 따로 부른다.

스타일 룰이 새 코드를 막으면 룰을 끄는 쪽이 맞는지 먼저 판단한다. React 19 의 `react-hooks/set-state-in-effect` 는 끄지 않았다 — 걸린 네 자리가 전부 고칠 값이 있었다. 저장값·미디어쿼리는 `lib/clientStore.ts` 의 `useSyncExternalStore` 훅으로 읽고, 다른 값에서 따라오는 상태는 렌더 중에 고친다.

화면이 실제로 그려지는지는 typecheck·lint·build 가 못 잡는다. 콘솔 에러·죽은 내부 링크·접근성 위반·폰 폭 넘침까지는 `npm run e2e` 가 잡는다 — 테스트는 `frontend/e2e/`, 설정은 `playwright.config.ts` 다. 빌드본을 3100 포트에 직접 띄우므로 dev 서버(3000)와 안 부딪친다.

**배포본을 그대로 겨눌 수 있다.** `E2E_BASE_URL=https://... npm run e2e` 로 돌리면 서버를 안 띄우고 그 주소를 검사한다 — 빌드는 통과하는데 배포가 깨진 경우는 로컬 검사로 안 잡힌다. 2026-09-18 에 이것으로 라이브 `/public-data` 가 Railway 에서 `/api/jobs` 404 를 받는 것을 찾았다.

Vercel **프리뷰** 배포는 보호가 걸려 있어 이 방식이 안 먹는다. 브라우저 탭은 통과하지만 `request.get` 은 로그인 HTML 을 받는다. 공개된 프로덕션 주소로 겨눈다.

차트가 눈에 맞게 그려졌는지, 지도 마커가 제 자리인지는 기계가 못 센다. 그건 chrome-devtools MCP 로 페이지를 열어 본다.

`/youtube`·`/public-data` 는 백엔드가 `localhost:8080` 에 떠 있어야 데이터가 온다. 백엔드가 없을 때 나는 연결 실패는 e2e 가 결함으로 안 센다(`e2e/routes.ts` 의 `isBackendNoise`). 대신 백엔드를 띄우고 돌리면 `e2e/backend.spec.ts` 가 붙어서 프론트→스프링→외부 API 까지 한 줄로 확인한다. 백엔드가 없으면 그 파일만 통째로 건너뛴다. 띄우는 법은 [backend/CLAUDE.md](../backend/CLAUDE.md) 「로컬 실행」이고, 백엔드의 `cors.allowed-origins` 에 `http://localhost:3100` 이 들어 있어야 브라우저가 호출을 막지 않는다.

## doc 참조 트리거

아래 작업을 할 때만 해당 문서를 연다. 그 외에는 열지 않는다.

| 작업 | 문서 |
|---|---|
| `/public-data` 지도 마커·좌표·SDK 로딩 변경 | [doc/frontend/kakao-maps-sdk.md](../doc/frontend/kakao-maps-sdk.md) |
| 라이브러리 버전 확인이 필요할 때 | `package.json` 직접 확인 |
