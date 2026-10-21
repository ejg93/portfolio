#!/usr/bin/env bash
# 건드린 구역이 무엇을 돌릴지 정한다. HEAD 대비 바뀐 파일(미추적 포함)을 보고
# frontend/ 코드가 바뀌었으면 typecheck·lint·build, 화면·정적 HTML 이 바뀌었으면 e2e,
# backend/ 가 바뀌었으면 mvnw test 를 고른다.
# 출력은 실패했을 때만 보여 준다 — 통과한 빌드 로그는 읽을 것이 없다.
#
#   bash scripts/verify.sh          바뀐 구역만
#   bash scripts/verify.sh --all    전부
set -uo pipefail
cd "$(dirname "$0")/.."

# 비교 기준은 origin/main 과의 merge-base 다. HEAD 로 잡으면 커밋·리베이스 뒤에 diff 가 비어
# 검사를 하나도 안 돌리고 도장만 찍는다(2026-09-21 에 실제로 났다). main 에서 곧장 일할 때는 둘이 같다.
base=$(git merge-base HEAD origin/main 2>/dev/null || git rev-parse HEAD)
changed=$( { git diff "$base" --name-only 2>/dev/null; git ls-files --others --exclude-standard; } | sort -u )
hit() { [ "${1:-}" = "--all" ] || printf '%s\n' "$changed" | grep -qE "$2"; }
fe=0; be=0; e2=0
hit "${1:-}" '^frontend/(app|components|lib)/|^frontend/(package\.json|package-lock\.json|next\.config\.js|tsconfig\.json|tailwind\.config\.js|postcss\.config\.js|eslint\.config\.mjs)$' && fe=1
hit "${1:-}" '^backend/(src/|pom\.xml$|Dockerfile$|checkstyle\.xml$)' && be=1
# 앱 화면과 public/ 정적 HTML 을 둘 다 e2e 가 든다. 정적 HTML 은 typecheck·lint·build 가 한 줄도 안 본다
hit "${1:-}" '^frontend/(app|components|lib)/|^frontend/public/(study|game|docrules|jobhunt|toolbox)/|^frontend/e2e/|^frontend/playwright\.config\.ts$' && e2=1

# 초록일 때 작업트리 tree 해시를 도장으로 남긴다. push hook 이 이것만 보고 판단한다(hook-push-gate.sh).
# 도장은 git 디렉터리 안에 둔다. `.git` 을 그대로 쓰면 git worktree 에서 깨진다 —
# 거기선 `.git` 이 디렉터리가 아니라 진짜 위치를 적어 둔 파일이라 하위 경로로 못 쓴다.
# rev-parse 는 본 저장소에서 `.git`, worktree 에서 `.git/worktrees/<이름>` 을 준다.
# 작업트리마다 도장이 갈라지는 것이 맞다 — 트리가 다르면 검증 결과도 다르다.
GITDIR=$(git rev-parse --git-dir 2>/dev/null || echo .git)
stamp() {
  tmp=$(mktemp)
  GIT_INDEX_FILE="$tmp" git read-tree HEAD 2>/dev/null
  GIT_INDEX_FILE="$tmp" git add -A . 2>/dev/null
  GIT_INDEX_FILE="$tmp" git write-tree 2>/dev/null > "$GITDIR/verify-stamp"
  rm -f "$tmp"
}

log=$(mktemp); trap 'rm -f "$log"' EXIT
ok=1
if [ $fe = 1 ]; then
  echo "== frontend 바뀜 → npm run typecheck · npm run lint · npm run build"
  (cd frontend && npm run typecheck && npm run lint && NEXT_DIST_DIR=.next-verify npm run build) >"$log" 2>&1 || { tail -40 "$log"; ok=0; }
fi
if [ $e2 = 1 ] && [ $ok = 1 ]; then
  echo "== 화면 바뀜 → npm run e2e"
  # 백엔드가 없으면 backend.spec 은 조용히 건너뛴다. 「안 돌아서 통과」를 통과로 읽지 않게 여기서 밝힌다
  if curl -sf -m 2 http://localhost:8080/api/jobs >/dev/null 2>&1; then
    echo "   백엔드 8080 떠 있다 — 데이터 경로까지 본다"
  else
    echo "   백엔드 8080 없다 — backend.spec 은 건너뛴다(bash scripts/dev-up.sh 로 띄운다)"
  fi
  # 방금 빌드했으면 그 산출물을 그대로 띄운다. 안 했으면 e2e 가 알아서 빌드한다
  if [ $fe = 1 ]; then
    (cd frontend && E2E_SKIP_BUILD=1 E2E_DIST=.next-verify npm run e2e) >"$log" 2>&1 || { tail -40 "$log"; ok=0; }
  else
    (cd frontend && npm run e2e) >"$log" 2>&1 || { tail -40 "$log"; ok=0; }
  fi
fi
if [ $be = 1 ]; then
  echo "== backend 바뀜 → ./mvnw test + 선언 순서 검사 (JDK 17)"
  # 시스템 JAVA_HOME 은 JDK 11 이라 못 쓴다(backend/CLAUDE.md 「검증」). 리눅스 러너는 그대로.
  case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) export JAVA_HOME="C:/Program Files/Java/jdk-17.0.19" ;; esac
  (cd backend && ./mvnw -B -q test checkstyle:check) >"$log" 2>&1 || { grep -E 'ERROR|error:' "$log" | head -30; ok=0; }
fi
if [ $fe = 0 ] && [ $be = 0 ] && [ $e2 = 0 ]; then
  echo "코드도 화면도 안 바뀌었다 — 돌릴 것이 없다."
  stamp
  exit 0
fi
[ $ok = 1 ] && { stamp; echo "초록"; } || { echo "빨갛다"; exit 1; }
