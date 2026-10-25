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
