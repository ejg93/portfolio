# Portfolio

한 저장소에 성격이 다른 구역이 섞여 있다. 배포되는 웹앱과 개인 보관물이 같은 트리에 있으니, **작업 시작 전 구역부터 판별하고 그 구역 규칙을 따른다.**

## 구역 지도

| 경로 | 무엇이 들어 있나 | 규칙 |
|---|---|---|
| `frontend/` | Next.js 16 App Router 앱. Vercel 배포 | [frontend/CLAUDE.md](frontend/CLAUDE.md) |
| `backend/` | Spring Boot 4 REST API. Railway 배포 | [backend/CLAUDE.md](backend/CLAUDE.md) |
| `frontend/public/toolbox/` | 사내 폐쇄망에 반입하는 단일 파일 HTML 도구 모음 | [toolbox/CLAUDE.md](frontend/public/toolbox/CLAUDE.md) |
| `frontend/public/notes/` | 개인 학습 메모(마크다운) 보관 | 요청받은 파일만 수정한다. 정리·리팩터링 대상 아님 |
| `frontend/public/game/` | 게임 확률 계산기 HTML. 개인용 | 요청받은 파일만 수정한다 |
| `frontend/public/study/` | 학습 노트 HTML 6장 + 공통 셸(`shell.css`·`shell.js`). 숨은 경로 | 요청받은 파일만 수정한다. 노트 규격은 [frontend/CLAUDE.md](frontend/CLAUDE.md) 「/study」. 글 규칙 4·6 은 hook·CI 가 본다(「」 인용·SVG·pre 는 면제) |
| `frontend/public/docrules/` | 한글·엑셀 문서 배포 규칙 HTML. 폐쇄망 반입 대상 | 요청받은 파일만 수정한다 |
| `frontend/public/jobhunt/` | 지원 회사 평판·통근을 한 표에서 거르는 HTML. 개인용, 데이터는 localStorage | 요청받은 파일만 수정한다 |
| `doc/` | 외부 API 스펙·배포 설정처럼 코드가 답 못 하는 것만. 색인은 [doc/README.md](doc/README.md) | 각 구역 CLAUDE.md의 트리거에 걸릴 때만 읽는다 |
| `scripts/` | `verify.sh`(바뀐 구역 골라 검증)·`dev-up.sh`(백엔드·프론트 띄우기)·`doc-lint.sh`(존댓말·줄바꿈·경로 검사)·`linebreak-lint.js`·`href-lint.js`·`toolbox-lint.js`(폐쇄망 규칙)·`hook-doc-lint.sh`·`hook-push-gate.sh`(hook 입구) | `.claude/settings.json` 의 hook 이 부른다 |
| `doc/design-standards/` | 행안부 공공 DB 표준화 지침 등 외부 PDF 원본. 표준단어·도메인·코드 설계 근거 | 읽기 전용. 색인은 [design-standards/README.md](doc/design-standards/README.md), 트리거는 toolbox/CLAUDE.md |

`public/` 아래 notes·game·study·docrules·jobhunt는 앱 코드가 아니라 정적 보관물이다. 근처 작업 중이라도 요청 없이 손대지 않는다.

## 검증

작업을 끝내기 전에 `bash scripts/verify.sh` 를 돌린다. HEAD 대비 바뀐 파일을 보고 무엇을 돌릴지 고른다. 출력은 실패했을 때만 나온다.

| 무엇이 바뀌면 | 무엇을 돌리나 |
|---|---|
| `frontend/app`·`components`·`lib`, 빌드 설정 | `npm run typecheck` · `npm run build`(lint 포함) |
| 위에 더해 `frontend/public/` 의 study·game·docrules·jobhunt·toolbox, `frontend/e2e/` | `npm run e2e` — 라우트 10개와 정적 HTML 10장을 크로미엄으로 연다 |
| `backend/src`·`pom.xml`·`checkstyle.xml` | JDK 17 로 `./mvnw test checkstyle:check`(선언 순서) |

e2e 는 방금 만든 `.next-verify` 빌드를 그대로 띄워서 같은 산출물을 두 번 만들지 않는다.

스크립트가 안 보는 것은 **그 구역 CLAUDE.md 「검증」 절**이 든다 — 차트·지도가 눈에 맞게 그려졌는지, 그리고 백엔드를 띄워야 보이는 데이터 경로.

백엔드가 `localhost:8080` 에 없으면 `e2e/backend.spec.ts` 는 **조용히 건너뛴다**. verify.sh 가 그때 한 줄로 밝히니 그 줄을 보고 판단한다. 띄우려면 `bash scripts/dev-up.sh` 다.

`verify.sh` 가 초록이면 작업트리 지문을 `.git/verify-stamp` 에 남긴다. **`git push` 는 그 도장이 지금 트리와 같아야 나간다** — 다르면 hook 이 막는다(`hook-push-gate.sh`). 도장을 찍는 쪽이 검증이고, push 는 확인만 한다.

hook 은 이 기계에서만 돈다. 다른 기계나 웹에서 올라온 커밋은 hook 을 안 거치므로 `.github/workflows/verify.yml` 이 push·PR 마다 같은 검사를 다시 돌린다 — frontend typecheck·build, e2e(라우트 10개 실사용 검사), 글 규칙·줄바꿈·경로 lint, backend compile.

돌리지 못했으면 못 돌렸다고 밝힌다. 안 돌려보고 "동작한다"·"빌드 통과"라고 쓰지 않는다. 일부만 확인했으면 확인한 범위와 못 한 범위를 나눠서 적는다.

## Superpowers 와 잇는 법

절차는 Superpowers 플러그인(`obra/superpowers`, local 설치)이 굴리고, 관문은 이 저장소 검증 도구가 든다(2026-10-29 사용자 결정).
**이 파일이 Superpowers 스킬보다 앞선다.** 둘이 다르면 여기를 따른다(`using-superpowers` 「User instructions take precedence」).
**확인한 판: 7.0.0**(2026-10-29 연동 점검). 판이 오르면 아래 표와 「실행 규칙」「계획 끝」의 덮은 자리를 스킬 원문과 다시 댄다.

| 스킬 기본 동작 | 여기서는 |
|---|---|
| `brainstorming` — 메시지당 질문 하나 · spec 을 `docs/superpowers/specs/` 에 커밋 · builder-check 서브에이전트 · 시각 보조 탭 | **선호 선언**: 사람만 답할 질문은 한 메시지에 (가)/(나) 로 모아 묻는다 · spec 은 `.superpowers/specs/`(gitignore, 커밋 안 함) · builder-check 와 시각 보조는 안 쓴다 · 승인 뒤 같은 세션이 `writing-plans` 로 간다 |
| `writing-plans` — 계획을 `docs/superpowers/plans/` 에 · 헤더에 `subagent-driven-development (recommended)` · 쓴 뒤 검토와 실행 방식을 묻고 기다림 | 계획은 `.superpowers/plans/`. 헤더 REQUIRED SUB-SKILL 은 `superpowers:executing-plans` 하나. `Spec:` 줄은 `.superpowers/specs/` 의 그 파일이고, spec 없이 온 일이면 「없음 — 요구는 대화」. **검토도 실행 방식도 묻지 않는다** — spec 승인이 검토다. 계획 경로 한 줄만 알리고 `executing-plans` 로 간다 |
| `executing-plans` — Setup 에서 worktree · push·머지는 「물어야 하는 부작용」 · 끝에 `finishing-a-development-branch` | 아래 「실행 규칙」과 「계획 끝」. `subagent-driven-development` 는 사용자가 지목할 때만 |
| `using-git-worktrees` — 선언이 없으면 worktree 를 묻는다 | **worktree 선호: 쓰지 않는다. `EnterWorktree` 도 안 쓴다.** `main` 이면 묻지 않고 `work/YYYY-MM-DD-<주제>` 가지를 따서 제자리에서 친다. doc-lint hook 은 `CLAUDE_PROJECT_DIR` 의 git diff 로 고친 파일을 찾아서 worktree 변경을 못 본다 |
| `test-driven-development` 를 모든 구현에 · 설정 파일 예외는 사람 허락 | `backend/` 코드에만 건다. frontend 는 단위 시험이 없고 typecheck·e2e 가 시험이다. 단위 시험 틀을 새로 깔지 않는다. **예외 허락(2026-10-29)**: 문서·설정·마이그레이션만 건드리는 Task 와 frontend Task 는 「실패하는 테스트 먼저」 단계를 빼고, 그 Task 의 「Task 끝」 명령이 RED→GREEN 자리다. 원장에 `Ruling:` 한 줄 |
| `finishing-a-development-branch` 가 선택지 3개를 내밀고 기다림 | **부르지 않는다** — 메뉴를 덮어도 스킬이 「그대로 내밀고 기다리라」고 반박한다. 아래 「계획 끝」이 대신한다 |

`using-superpowers` 의 1% 규칙과 「plan mode 앞 brainstorming」은 창작에만 선다. 파일 2개 이하를 고치는 일 · 질문 답변 · 점검·검증·리뷰 · `/plan` 설계는 `brainstorming`·`writing-plans` 없이 바로 한다.
전역 CLAUDE.md 의 플랜 형식(8절)은 `/plan` 플랜 모드에만 걸린다. `writing-plans` 계획 파일은 스킬 형식을 따른다.

**실행 규칙** — `executing-plans` 로 칠 때

- 한 세션이 인라인으로 친다. Task 마다 서브에이전트를 안 띄운다
- push·PR·머지는 묻지 않는다 — 2026-10-29 사용자가 위임했다. `executing-plans` 의 「물어야 하는 부작용」에 해당하지 않는다
- **Task 끝** — 바뀐 구역의 빠른 검사 초록 → 커밋 → 같은 명령으로 `task-done PLAN N BASE -- <명령>`. `task-done` 은 명령을 `"$@"` 로 직접 실행해서 `JAVA_HOME=… ./mvnw` 꼴 환경 접두가 안 돈다. 명령은 셋 중 하나다
  - frontend: `bash -c 'cd frontend && npm run typecheck'`
  - backend: `bash -c 'cd backend && JAVA_HOME="C:/Program Files/Java/jdk-17.0.19" ./mvnw -B -q test'`
  - 문서만: `bash scripts/doc-lint.sh <고친 파일>`

**계획 끝**

| 순서 | 무엇 |
|---|---|
| 1 | **독립 리뷰 = `executing-plans` 의 Final Review 하나** — `review-package PLAN $(git merge-base origin/main HEAD) HEAD` 패키지 + `code-reviewer.md`, 서브에이전트에 `model: fable` 명시. 계획 Review Focus 에 이 저장소 질문을 적어 둔다: 요청 밖 `public/` 정적 보관물을 건드렸나 · 글 작성 규칙 1·2·3·5(기계가 못 잡는 것) · 새 라우트가 LAB 메뉴·sitemap·e2e 라우트 목록에 올랐나 · 키·토큰이 diff 에 섞였나. 대화는 안 준다. 따로 리뷰를 또 돌리지 않는다 |
| 2 | 처분 — Critical·Important 는 한 번의 고침 패스로 지금 고쳐 커밋, Minor 는 PR 본문 「Deferred minors」. 오탐도 근거를 적는다 |
| 3 | `bash scripts/verify.sh` 초록. 브랜치 전체를 보고 push 도장을 찍는다. **2 의 고침 커밋 뒤에 돈다** — 먼저 돌면 고침이 지문을 바꿔 push hook 이 막는다. 빨가면 고치고 3 만 다시 돈다 |
| 4 | push · PR. 본문에 원장 `.superpowers/sdd/<계획>/progress.md` 의 `Ruling:` 줄과 Deferred minors 를 옮긴다. 옮긴 뒤 워크스페이스를 지운다 |
| 5 | `gh pr checks N --watch` 초록 뒤 `gh pr merge N --merge` 를 **단독 명령으로** 낸다. `cd`·`&&`·파이프를 붙이면 허용 규칙에 안 걸린다. squash·`--auto` 는 안 쓴다 |
| 6 | `backend/` 가 바뀌었으면 deploy-backend 운영 반영을 확인한다 |

## 토큰 절약

- 이미 읽은 파일 다시 읽지 않는다. 수정 직후 확인용 재읽기도 안 한다
- 큰 파일은 필요한 범위만 읽는다 (offset/limit)
- "어디에 정의됐나", "뭐가 호출하나" 류 탐색은 서브에이전트에 위임한다
- 검색 시 `node_modules/`, `target/`, `.next/`, `*.tsbuildinfo` 제외
- `doc/design-standards/`와 `public/toolbox/db_docs/`는 **통짜 grep만 금지**다. 대용량 PDF·벤더 문서 트리라 전문 검색이 무의미하다. 각 폴더 `README.md`의 라우팅 표를 읽고 지목된 파일만 연다 — 참조 자체를 피하라는 뜻이 아니다
- 빌드·테스트 출력은 실패한 부분만 인용한다. 전체 로그 붙여넣지 않는다
- 작업 하나 끝나면 `/compact` 권한다

## 배포

| 서비스 | 플랫폼 | 트리거 |
|---|---|---|
| frontend | [Vercel](https://vercel.com/ejg93s-projects/ejgsproject) | `frontend/` 루트, `main` push 시 자동 |
| backend | [Railway](https://railway.com/dashboard) | `backend/` 루트. verify 가 `main` 에서 초록일 때 `deploy-backend.yml` 이 올린다 |

`public/` 아래 정적 파일도 Vercel 배포에 그대로 실린다. 개인 메모·게임 파일을 커밋하면 공개 URL로 접근 가능해진다.

상세 설정·체크리스트는 [doc/deployment.md](doc/deployment.md).

## 글 작성 규칙 (문서·UI 문구 공통, 반드시 준수)

1. **중복 금지** — 같은 내용을 다른 표현으로 두 번 이상 쓰지 말 것. 앞에서 이미 한 말을 반복하는 문장은 삭제.
2. **사견 배제** — 개인적 해석이나 평가를 끼워넣지 말 것. 사용자가 실제로 한 말과 표현한 감정만 바탕으로 정리. 사용자가 하지 않은 생각을 대신 만들어 붙이지 말 것.
3. **늘어지지 않게** — 분량을 채우려고 미사여구나 뻔한 문장으로 늘리지 말 것. 필요한 만큼만 쓰고 끝낼 것.
4. **존댓말 금지** — `~습니다` `~읍니다` `~합니다` `~하세요` `~입니다` 금지.
   평서형으로 쓴다. `~한다` `~했다` `~함` `~됨` 모두 허용. 명사형으로만 끝내라는 뜻이 아니다.
   **여섯 중 4번과 6번을 기계가 잡는다** — 파일을 고칠 때마다 hook 이 `scripts/doc-lint.sh` 로 고친 줄을 보고, 걸리면 막는다. 남의 말을 옮긴 것이면 md 에서는 백틱이나 「」로 감싼다.
5. **자기 기능만 설명** — 어떤 항목의 설명란에는 그 항목이 하는 일만 쓴다.
   - 다른 항목이 뭘 맡는지, 여기서 안 되는 걸 어디로 가져가라는 안내는 **그 항목 설명란**에 쓴다.
   - `A로 안 되는 걸 하는 자리` 처럼 남을 기준으로 자기를 정의하지 말 것. 읽는 쪽이 A 설명을 먼저 봐야 이해되는 순환 구조가 된다.
   - 비교·우선순위를 꼭 써야 하면 상대 이름 대신 **자기 동작**으로 서술한다. (`1번보다 먼저 채택` ✗ → `토큰 조립보다 먼저 채택` ○)
6. **줄바꿈은 렌더된 화면 기준** — HTML은 소스의 개행이 화면에서 사라진다. 소스에서 줄을 나눠도 브라우저가 한 줄로 이어 붙이므로, 끊으려면 `<br>` 을 써야 한다. 아래 판정은 전부 **화면에 실제로 그려진 줄** 기준이다.

   **끊는다**
   - 한 줄에 **문장이 2개 이상** 들어갈 때 → 문장 경계마다 `<br>`
   - 문장 1개가 **줄 폭의 70% 이상**을 채울 때 → 의미 단위(`—`, 절 경계, 괄호 앞)에서 `<br>`
   - 표 셀(`<td>`)도 같은 기준. 열 폭이 좁아 문장 2개가 들어가면 더 빨리 뭉갠다

   **안 끊는다**
   - 짧은 문장 2개가 합쳐도 70%를 못 채우면 붙여 둔다. 잘게 쪼개면 그것대로 지저분하다
   - 문장 중간을 잘라야만 70% 밑으로 내려가는 경우. 읽기가 더 나빠진다
   - **컨트롤 옆 인라인 힌트** — 체크박스·입력칸·버튼과 한 줄에 놓인 `<span>` 설명. `<br>` 을 넣으면 컨트롤 줄 배치가 깨진다
   - `title=` 속성 안. 브라우저 기본 툴팁은 `<br>` 을 태그로 안 읽고 그대로 출력한다. 줄을 나눠야 하면 실제 개행 문자를 쓴다
   - 링크 뒤에 붙은 `←` 화살표 라벨. 끊으면 화살표가 가리킬 대상이 윗줄로 떨어진다

   **`—` 로 이어지는 문장을 끊을 때는 `—` 를 앞줄 끝에 남긴다.** 뒷줄이 이어짐을 알 수 있다.
   ```html
   ... 스크립트를 비교 대상으로 잡고 병합한다 —<br>
   되긴 하지만 매핑을 손으로 봐야 한다.
   ```

   **들여쓴 하위 줄은 `&nbsp;&nbsp;` 로 붙인다.** `·` 로 시작하는 항목의 이어지는 줄이 항목 머리와 같은 위치에서 시작하면 새 항목처럼 보인다.

   **검증** — `<br>` 과 블록 태그로 자른 조각의 폭을 세서 확인한다. 한글은 2폭으로 계산하고, 산문 조각이 **95폭**을 넘으면 다시 본다.
   tsx·html 의 고친 줄은 `scripts/linebreak-lint.js` 가 센다 — 95폭 초과 조각, 문장 끝에서 다음 소스 줄로 이어지는 본문(브라우저가 한 줄로 붙인다), 한 줄에 두 문장이면서 80폭 초과.
   데이터 문자열은 `\n` 을 끊음으로 친다(`white-space: pre-line` 으로 그릴 것).
