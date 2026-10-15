import fs from 'fs'
import path from 'path'
import { S, Section } from '@/components/CaseStudy'

export const metadata = { title: 'TOOLBOX' }

const REPO_PORTFOLIO = 'https://github.com/ejg93/public'
const SAMPLE_DIR = encodeURI('/toolbox/논리명_변환기_sample')

type Tool = { file: string; icon: string; name: string; desc: string }

// 도구 목록의 단일 진실은 런처 toolbox.html 의 TOOLS 배열이다(toolbox/CLAUDE.md 규칙 5).
// 세 번째 사본을 만들지 않으려고 빌드 때 그 파일을 읽어 뽑는다.
// 런처의 객체 키(file·icon·name·desc)를 바꾸면 아래 정규식도 같이 고친다.
function readTools(): Tool[] {
  const html = fs.readFileSync(path.join(process.cwd(), 'public/toolbox/toolbox.html'), 'utf8')
  const re = /\{\s*file:\s*"([^"]+)",\s*icon:\s*"([^"]+)",\s*name:\s*"([^"]+)",\s*desc:\s*"([^"]+)"\s*\}/g
  const tools: Tool[] = []
  let m: RegExpExecArray | null
  while ((m = re.exec(html)) !== null) {
    tools.push({ file: m[1], icon: m[2], name: m[3], desc: m[4] })
  }
  if (tools.length !== 8) {
    throw new Error(`toolbox.html 에서 도구 ${tools.length}개를 뽑았다 — 8개여야 한다`)
  }
  return tools
}

const RULES = [
  {
    title: '외부 CDN·npm 금지',
    body: '스크립트·웹폰트·외부 이미지를 밖에서 안 불러온다.\n폐쇄망에서는 로드 자체가 실패한다.\n라이브러리가 필요하면 파일 안에 인라인으로 넣는다.',
  },
  {
    title: '파일 하나로 완결',
    body: 'HTML·CSS·JS 를 한 파일에 담는다.\n도구를 여러 파일로 쪼개지 않는다.',
  },
  {
    title: '개인정보 localStorage 저장 금지',
    body: '주민번호·사업자번호를 넣고 돌리는 입력칸은 값을 안 남긴다.\n스키마명·즐겨찾기처럼 개인정보가 아닌 설정만 저장한다.',
  },
]

// 샘플 세트는 논리명 변환기 카드에만 붙는다. 거의 안 바뀌어 여기 박아 둔다
const SAMPLES = [
  { name: '행정안전부_공공데이터 공통표준단어_20251101.csv', slot: '1번 칸' },
  { name: '샘플_기관표준단어.csv', slot: '2번 칸' },
  { name: '샘플_컬럼목록1000개.csv', slot: '3번 칸' },
  { name: '보조_공통약어사전.csv', slot: '보조 사전' },
  { name: '샘플_컬럼목록1000개_README.md', slot: '기대 수치' },
]

const SOURCES = [
  {
    title: '행안부 공공데이터 표준화 지침',
    body: '논리명 변환기가 영문 컬럼명을 한글로 조립할 때 근거로 삼는 표준단어·도메인 원문.',
    href: `${REPO_PORTFOLIO}/blob/main/doc/design-standards/README.md`,
    label: 'doc/design-standards/README.md',
  },
  {
    title: 'DB 벤더 딕셔너리 라우팅',
    body: '산출물 SQL 이 벤더별 딕셔너리 뷰를 고를 때 어느 매뉴얼의 어느 파일을 볼지 적어 둔 표.',
    href: `${REPO_PORTFOLIO}/blob/main/frontend/public/toolbox/db_docs/README.md`,
    label: 'frontend/public/toolbox/db_docs/README.md',
  },
]

const linkStyle = {
  fontSize: '11px',
  color: 'var(--accent)',
  textDecoration: 'none',
  wordBreak: 'break-all' as const,
}

export default function Toolbox() {
  const tools = readTools()

  return (
    <div style={{ paddingTop: '20px', maxWidth: '860px' }}>

      {/* ── 헤더 ───────────────────────────────────── */}
      <div className="mono" style={{ fontSize: '11px', color: 'var(--accent)', letterSpacing: '2px', marginBottom: '14px' }}>
        폐쇄망 도구
      </div>

      <h1 className="display display-xl" style={{ lineHeight: 0.95, marginBottom: '24px' }}>
        <span style={{ color: 'var(--text)' }}>TOOL</span><br />
        <span style={{ color: 'var(--accent)' }}>BOX</span>
      </h1>

      <p style={{ ...S.body, fontSize: '15px', marginBottom: '12px' }}>
        사내 폐쇄망 PC 에 HTML 파일 하나만 복사해서 여는 개발 보조 도구
      </p>
      <p style={{ ...S.body, fontSize: '14px', marginBottom: '20px' }}>
        디지털돌봄 운영 단계에서 산출물 문서 작업에 필요한 기능과<br />
        그때그때 떠오른 기능을 한 묶음으로 모아 계속 패치하여 만들어졌다.<br />
        논리명 변환기는 Erd 문서 작성을 지원할 때 추가했다.<br />
      </p>

      <div style={{ marginBottom: '56px' }}>
        <a href="/toolbox/toolbox.html" target="_blank" rel="noopener noreferrer" className="mono" style={{
          display: 'inline-flex', alignItems: 'center', gap: '8px',
          padding: '11px 20px', borderRadius: '6px', textDecoration: 'none',
          background: 'var(--accent)', color: 'var(--bg)',
          fontSize: '13px', fontWeight: 700, letterSpacing: '1px',
        }}>
          ⌥ 런처 원본 열기 →
        </a>
      </div>

      {/* ── 절대 규칙 ──────────────────────────────── */}
      <Section label="01 · 절대 규칙">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(230px, 1fr))', gap: '10px' }}>
          {RULES.map(r => (
            <div key={r.title} style={S.card}>
              <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text)', marginBottom: '10px' }}>{r.title}</div>
              <p style={{ ...S.body, fontSize: '13px', margin: 0 }}>{r.body}</p>
            </div>
          ))}
        </div>
      </Section>

      {/* ── 도구 ───────────────────────────────────── */}
      <Section label="02 · 도구">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {tools.map(t => {
            const href = `/tools/${encodeURI(t.file.replace(/^tools\//, ''))}`
            const isNaming = t.file.includes('논리명_변환기')
            const isSqlDoc = t.file.includes('산출물_sql')
            return (
              <div key={t.file} style={S.card}>
                <div style={{ display: 'flex', gap: '12px', alignItems: 'baseline', flexWrap: 'wrap', marginBottom: '10px' }}>
                  <span style={{ fontSize: '18px' }}>{t.icon}</span>
                  <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text)' }}>{t.name}</span>
                  <a href={href} target="_blank" rel="noopener noreferrer" className="mono" style={{ ...linkStyle, fontSize: '12px' }}>
                    열기 →
                  </a>
                </div>
                <p style={{ ...S.body, fontSize: '13px', margin: 0 }}>{t.desc}</p>
                {isSqlDoc && (
                  <p style={{ ...S.body, fontSize: '13px', margin: '8px 0 0' }}>
                    DB 없이도 SQL 은 생성되고 복사할 수 있다.
                  </p>
                )}
                {isNaming && (
                  <div style={{ marginTop: '12px', paddingTop: '12px', borderTop: '1px solid var(--border)' }}>
                    <div className="mono" style={{ fontSize: '10px', color: 'var(--accent2)', letterSpacing: '2px', marginBottom: '8px' }}>
                      샘플
                    </div>
                    <p style={{ ...S.body, fontSize: '13px', margin: '0 0 10px' }}>
                      빈 입력칸으로 열리는 도구라 넣을 것이 있어야 돌아간다.<br />
                      아래 셋을 각각 1·2·3번 칸에 넣고,<br />
                      「테이블 접두 무시토큰」에 TB 를 적고 돌리면,<br />
                      {/* 이 수치는 샘플 README 의 기대값이다. README 가 바뀌면 여기도 바꾼다 */}
                      결과표 상단에 <code style={{ color: 'var(--text)' }}>컬럼 944 · 완전매칭 589 (56.2%)</code> 가 나온다.
                    </p>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                      {SAMPLES.map(s => (
                        <a key={s.name} href={`${SAMPLE_DIR}/${encodeURI(s.name)}`} target="_blank" rel="noopener noreferrer"
                          className="mono tap" style={linkStyle}>
                          {s.slot} · {s.name} →
                        </a>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      </Section>

      {/* ── 근거 자료 ──────────────────────────────── */}
      <Section label="03 · 근거 자료">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {SOURCES.map(s => (
            <div key={s.href} style={S.card}>
              <div style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text)', marginBottom: '8px' }}>{s.title}</div>
              <p style={{ ...S.body, fontSize: '13px', margin: '0 0 10px' }}>{s.body}</p>
              <a href={s.href} target="_blank" rel="noopener noreferrer" className="mono" style={linkStyle}>
                {s.label} →
              </a>
            </div>
          ))}
        </div>
      </Section>
    </div>
  )
}
