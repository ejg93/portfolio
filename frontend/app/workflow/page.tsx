import { S, Section } from '@/components/CaseStudy'
import {
  QUOTE_STOP, QUOTE_PUSH, QUOTE_PR_BASE, QUOTE_PR_SERIAL,
  QUOTE_DOC_DATE, QUOTE_DOC_STYLE, QUOTE_REQ, QUOTE_LINEBREAK,
} from './quotes'

export const metadata = { title: 'AI WORKFLOW' }

const REPO_SHOP = 'https://github.com/ejg93/ProjectShop'
const REPO_PORTFOLIO = 'https://github.com/ejg93/portfolio'
const REPO_CHUNKFRAME = 'https://github.com/ejg93/chunkframe'

const SKELETON = [
  {
    word: '예열',
    body: 'CLAUDE.md 규칙과 PROGRESS.md 진행 상태를 읽고\n오늘 청크에 걸릴 축을 고른다.\n코드는 안 건드린다.',
  },
  {
    word: '청크',
    body: '분할표에서 하나를 잡아 친다.\n파일 1~3개, 커밋 1개로 떨어지게 자른다.',
  },
  {
    word: '마무리',
    body: '이 세션에서 친 청크를 한꺼번에 대조하고\nPR 을 하나 연다.',
  },
  {
    word: '점검',
    body: '저장소 전체를 한 축으로 훑는다.\n이번에 안 건드린 파일에서 나오는 것을 잡는다.',
  },
]

const GATES = [
  {
    title: '커밋 안 된 작업물이 있으면 세션이 못 멈춘다',
    when: 'Stop hook',
    quotes: [QUOTE_STOP],
    links: [{ label: '.claude/settings.json', href: `${REPO_SHOP}/blob/main/.claude/settings.json` }],
  },
  {
    title: '검증이 초록이 아니면 push 가 안 나간다',
    when: 'git push 직전',
    quotes: [QUOTE_PUSH],
    links: [
      { label: '.claude/settings.json', href: `${REPO_SHOP}/blob/main/.claude/settings.json` },
      { label: 'scripts/verify.sh', href: `${REPO_SHOP}/blob/main/scripts/verify.sh` },
    ],
  },
  {
    title: 'PR 은 main 을 base 로, 한 번에 하나만',
    when: 'gh pr create 직전',
    quotes: [QUOTE_PR_BASE, QUOTE_PR_SERIAL],
    links: [{ label: '.claude/settings.json', href: `${REPO_SHOP}/blob/main/.claude/settings.json` }],
  },
  {
    title: '문서가 부서지면 저장 직후 막힌다',
    when: '문서를 고친 직후',
    quotes: [QUOTE_DOC_DATE, QUOTE_DOC_STYLE],
    links: [{ label: 'scripts/doc-lint.sh', href: `${REPO_SHOP}/blob/main/scripts/doc-lint.sh` }],
  },
  {
    title: '법 요건 중 테스트가 안 부르는 것을 센다',
    when: '점검 때. 게이트가 아니라 리포트',
    quotes: [QUOTE_REQ],
    links: [{ label: 'scripts/req-coverage.sh', href: `${REPO_SHOP}/blob/main/scripts/req-coverage.sh` }],
  },
  {
    title: '이 포트폴리오 저장소는 글 규칙을 어긴 줄에서 막는다',
    when: '파일을 고친 직후',
    quotes: [QUOTE_LINEBREAK],
    links: [{ label: 'scripts/linebreak-lint.js', href: `${REPO_PORTFOLIO}/blob/main/scripts/linebreak-lint.js` }],
    note: '검사기를 붙인 직후 hook 이 40번 줄을 잡았다.\n오탐이었고, 검사기를 고쳤다 — 2026-09-12.',
  },
]

const TEMPLATE = [
  { file: 'CLAUDE.md', desc: '세션 생명주기 4단계, 라우팅 표, 규칙 우선순위' },
  { file: 'PLAN.md · PROGRESS.md', desc: '청크 분할표와 진행 로그의 빈 틀' },
  { file: 'doc/reference/document-map.md', desc: 'D-번호 문서 카탈로그 틀' },
  { file: 'scripts/doc-lint.sh', desc: 'CLAUDE.md·doc/reference 문서에서 제목 손상과 중복 문장을 찾아 찍는 검사기' },
]

const quoteBox = {
  fontSize: '12px',
  lineHeight: 1.7,
  color: 'var(--text)',
  background: 'var(--surface)',
  border: '1px solid var(--border)',
  borderRadius: '6px',
  padding: '12px 14px',
  margin: '0 0 8px',
  whiteSpace: 'pre-wrap' as const,
  wordBreak: 'break-word' as const,
  fontFamily: 'var(--font-mono), monospace',
}

const linkStyle = {
  fontSize: '11px',
  color: 'var(--accent)',
  textDecoration: 'none',
  wordBreak: 'break-all' as const,
}

export default function Workflow() {
  return (
    <div style={{ paddingTop: '20px', maxWidth: '860px' }}>

      {/* ── 헤더 ───────────────────────────────────── */}
      <div className="mono" style={{ fontSize: '11px', color: 'var(--accent)', letterSpacing: '2px', marginBottom: '14px' }}>
        작업 방식
      </div>

      <h1 className="display display-xl" style={{ lineHeight: 0.95, marginBottom: '24px' }}>
        <span style={{ color: 'var(--text)' }}>AI</span><br />
        <span style={{ color: 'var(--accent)' }}>WORKFLOW</span>
      </h1>

      <p style={{ ...S.body, fontSize: '15px', marginBottom: '56px' }}>
        문서에 적은 규칙을 검사기로 옮기고, 검사기가 어긴 줄에서 막게 한다
      </p>

      {/* ── 경계 ────────────────────────────────────── */}
      <Section label="00 · 무엇을 도구가 하고 무엇을 내가 하나">
        <div style={S.card}>
          <p style={{ ...S.body, fontSize: '14px', margin: 0 }}>
            이 사이트와 ProjectShop 의 코드는 Claude Code 가 짠다.<br />
            내가 하는 일은 셋이다.<br />
            무엇을 만들지 요구사항으로 적고, 갈림길에서 결정하고, 나온 것을 검증한다.<br />
            셋 중 검증은 사람 눈에 안 맡기고 hook·lint·CI 로 넘겼다.<br />
            아래가 그 목록이다.<br />
            폐쇄망 회사 환경에서는 담당 프로젝트와 비슷한 구성을 만들어 같은 순서로 돌린다.
          </p>
        </div>
      </Section>

      {/* ── 세션의 뼈대 ─────────────────────────────── */}
      <Section label="01 · 세션의 뼈대">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: '10px' }}>
          {SKELETON.map(s => (
            <div key={s.word} style={S.card}>
              <div className="mono" style={{ fontSize: '11px', color: 'var(--accent2)', letterSpacing: '2px', marginBottom: '10px' }}>
                {s.word}
              </div>
              <p style={{ ...S.body, fontSize: '13px', margin: 0 }}>{s.body}</p>
            </div>
          ))}
        </div>
        <a href="/study/ai-native-notes" className="mono" style={{ ...linkStyle, fontSize: '12px', display: 'inline-block', marginTop: '16px' }}>
          전체 노트 →
        </a>
      </Section>

      {/* ── 강제 지점 ──────────────────────────────── */}
      <Section label="02 · 자동 검사가 막는 것">
        <p style={{ ...S.body, marginBottom: '18px' }}>
          아래 여섯은 막힐 때 화면에 실제로 찍히는 메시지를 그대로 옮긴 것이다.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {GATES.map(g => (
            <div key={g.title} style={S.card}>
              <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text)', marginBottom: '6px' }}>{g.title}</div>
              <div className="mono" style={{ fontSize: '11px', color: 'var(--accent2)', letterSpacing: '1px', marginBottom: '12px' }}>
                {g.when}
              </div>
              {g.quotes.map(q => (
                <pre key={q.slice(0, 24)} style={quoteBox}>{q}</pre>
              ))}
              {g.note && (
                <p style={{ ...S.body, fontSize: '12px', margin: '10px 0 0' }}>{g.note}</p>
              )}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '12px' }}>
                {g.links.map(l => (
                  <a key={l.href} href={l.href} target="_blank" rel="noopener noreferrer" className="mono tap" style={linkStyle}>
                    {l.label} →
                  </a>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Section>

      {/* ── 기록과 강제 지점 ────────────────────────── */}
      <Section label="03 · 기록은 재발을 못 막고 강제 지점은 막는다">
        <div style={S.card}>
          <p style={{ ...S.body, margin: 0 }}>
            「뷰가 컬럼을 굳힌다」는 함정을 진행 로그에만 적었더니<br />
            다음 날 같은 자리를 다시 밟았다.<br />
            같은 내용을 대조 테스트로 내리자 그다음 마이그레이션에서 바로 잡혔다.
          </p>
        </div>
      </Section>

      {/* ── 템플릿 ─────────────────────────────────── */}
      <Section label="04 · 템플릿">
        <div style={S.card}>
          <p style={{ ...S.body, marginBottom: '16px' }}>
            chunkframe 은 ProjectShop 에서 커머스·한국법 같은 도메인 색을 빼고<br />
            메커니즘만 남긴 틀이다. 새 저장소는 여기서 시작한다.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px' }}>
            {TEMPLATE.map(t => (
              <div key={t.file} style={{ display: 'flex', gap: '14px', alignItems: 'baseline', flexWrap: 'wrap' }}>
                <span className="mono" style={{ fontSize: '11px', color: 'var(--accent3)', minWidth: '200px' }}>{t.file}</span>
                <span style={{ ...S.body, fontSize: '13px' }}>{t.desc}</span>
              </div>
            ))}
          </div>
          <a href={REPO_CHUNKFRAME} target="_blank" rel="noopener noreferrer" className="mono" style={{ ...linkStyle, fontSize: '12px' }}>
            ejg93/chunkframe →
          </a>
        </div>
      </Section>
    </div>
  )
}
