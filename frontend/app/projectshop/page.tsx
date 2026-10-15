import Image from 'next/image'
import { S, Section } from '@/components/CaseStudy'
import Shot from '@/components/Shot'
import { getShopStats } from '@/lib/github'

export const metadata = { title: 'PROJECT SHOP' }

const REPO_SHOP = 'https://github.com/ejg93/ProjectShop'
// Railway 에 올린 ProjectShop 프론트. 상품 목록 화면으로 바로 들어간다
const DEMO_SHOP = 'https://frontend-production-b83c.up.railway.app/products'

// GitHub 호출이 실패했을 때만 쓰는 반올림 값. 기준 2026-09
const FALLBACK = [
  { n: '590+', label: '커밋' },
  { n: '11', label: '설계 기록(ADR)' },
  { n: '60+', label: 'Flyway 마이그레이션' },
  { n: '40+', label: '기술 문서' },
  { n: '110+', label: '테스트 파일' },
  { n: '6', label: 'CI 워크플로' },
]

// 요건 R21 하나가 법에서 CI 까지 이어지는 줄기. 링크는 전부 ProjectShop 저장소 파일
const TRACE = [
  {
    step: '법',
    body: '전자상거래법 제15조제1항 — 공급 약정이 없으면 결제일부터 3영업일 안에 발송.\n요건표 R21 이 이 조항을 받는다.',
    links: ['doc/reference/commerce-compliance.md'],
  },
  {
    step: 'DB 제약',
    body: 'V26__supply_deadline.sql 이 seller_order.supply_lead_days 를\nnot null default 3 · check (0~60) 으로 박고,\nship_due_at 을 결제 승인 때 박제한다.',
    links: ['backend/src/main/resources/db/migration/V26__supply_deadline.sql'],
  },
  {
    step: '테스트',
    body: 'ShipDeadlineTest 가 기한 계산과 미발송 판정을 고정한다.',
    links: ['backend/src/test/java/com/projectshop/shop/order/ShipDeadlineTest.java'],
  },
  {
    step: 'CI·화면',
    body: 'ci.yml 의 backend 잡이 push 마다 ./gradlew build 로 그 테스트를 돌린다.\n화면은 checkout/summary.tsx 가 기한을 고지한다.',
    links: ['.github/workflows/ci.yml', 'frontend/src/app/checkout/summary.tsx'],
  },
]

// 그림은 public/images 의 svg. 1200×675 고정, 다크 배경이라 라이트 테마에서는 테두리로 구분한다
function Figure({ src, alt }: { src: string; alt: string }) {
  return (
    <Image src={src} alt={alt} width={1200} height={675} unoptimized
      style={{ width: '100%', height: 'auto', border: '1px solid var(--border)', borderRadius: '8px', display: 'block' }} />
  )
}

// 배포본에서 찍은 화면. w·h 는 png 실측값이라 파일을 갈면 같이 고친다
const SHOTS = [
  {
    src: '/images/shop/shop-products.png', w: 1440, h: 1125,
    alt: '판매자 둘의 상품이 한 목록에 섞여 있고 카드마다 배송비가 따로 적힌 화면',
    cap: '판매자 둘이 올린 상품을 한 목록에 섞고, 배송비를 판매자마다 따로 적는다.',
  },
  {
    src: '/images/shop/shop-product-detail.png', w: 1440, h: 1694,
    alt: '색상·크기 옵션과 발송 기한, 통신판매중개자 고지, 판매자 신원이 차례로 놓인 상품 상세 화면',
    cap: '옵션 두 축과 발송 기한 아래에 통신판매중개자 고지와 판매자 신원을 같이 싣는다.',
  },
  {
    src: '/images/shop/shop-seller-form.png', w: 1440, h: 1100,
    alt: '상품명·판매가·재고 칸 아래에 표시·광고 근거 문구와 출처 주소 칸이 붙은 등록 화면',
    cap: '판매자가 상품을 올리는 화면. 「국내 1위」 같은 문구에는 근거와 출처를 같이 받는다.',
  },
  {
    src: '/images/shop/shop-checkout.png', w: 1440, h: 900,
    alt: '판매자별로 묶인 장바구니. 묶음마다 배송비가 따로 붙고 합계가 아래에 있다',
    cap: '담은 상품을 판매자별로 묶고, 배송비를 묶음마다 더해 결제 금액을 낸다.',
  },
]

// 같은 주소를 역할만 바꿔 연 화면. 앞 둘은 머리만 잘라 메뉴 차이를 맞대 놓는다
const ROLE_SHOTS = [
  {
    src: '/images/shop/shop-role-seller.png', w: 1440, h: 220,
    alt: '판매자로 로그인한 상태의 상단 메뉴. 받은 주문·내 상품·받은 문의·정산서가 보인다',
    cap: '판매자로 연 상품 목록의 머리. 받은 주문·내 상품·받은 문의·정산서가 붙는다.',
  },
  {
    src: '/images/shop/shop-role-customer.png', w: 1440, h: 220,
    alt: '구매자로 로그인한 상태의 상단 메뉴. 상품·장바구니·내 주문 셋만 보인다',
    cap: '구매자로 연 같은 주소. 메뉴가 상품·장바구니·내 주문 셋에서 끝난다.',
  },
  {
    src: '/images/shop/shop-role-denied.png', w: 1440, h: 900,
    alt: '구매자 계정으로 판매자 전용 주소를 열었을 때 나오는 차단 화면',
    cap: '구매자가 판매자 전용 주소를 주소창에 직접 쳐도 화면이 막힌다.',
  },
]

const DECISIONS: { id: string; title: string; body: string; image?: { src: string; alt: string } }[] = [
  {
    id: 'ADR-0003',
    title: '스코프는 role_permission 행에 둔다',
    image: { src: '/images/ps-role-rows.svg', alt: '같은 권한이 seller 스코프 allow 와 own 스코프 deny 두 행으로 잡힌 표' },
    body: '역할과 권한을 테이블로 분리하고, 그 연결 행에 own·seller·all 같은 행 단위 조건을 같이 싣는다.\n권한 판정이 코드 곳곳의 if 문이 아니라 한 테이블 조회로 끝난다.',
  },
  {
    id: 'PLAN.md → ADR-0010',
    title: '인증은 세션 쿠키로 하고 JWT를 쓰지 않는다',
    body: 'HttpOnly · SameSite 쿠키에 세션을 담아 서버가 상태를 쥔다.\n권한 체계가 주제인 프로젝트에서 판매자 권한 박탈이 즉시 먹어야 하는데,\n서버가 회수할 수 없는 토큰으로는 그게 안 된다.\n끊는 지점도 두 겹으로 뒀다.\n인증 필터가 요청마다 계정 생존을 확인하고,\n탈퇴 시 SessionRegistry가 그 사람 세션을 전부 만료시킨다.',
  },
  {
    id: 'ADR-0001',
    title: '스키마 변경은 Flyway로 관리한다',
    body: 'DDL을 버전 붙은 SQL 파일로 저장소에 남기고, 앱이 뜰 때 순서대로 적용한다.\n스키마가 코드와 같은 리뷰·이력을 받는다.',
  },
  {
    id: 'ADR-0009',
    title: '공휴일은 데이터로, 상태 전이표는 코드로 둔다',
    body: '해마다 바뀌는 공휴일은 DB 행으로 넣어 배포 없이 고치고,\n주문 상태 전이는 코드 상수로 박아 컴파일러와 테스트가 잡게 한다.\n바뀌는 주기가 다른 둘을 같은 층에 두지 않는다.',
  },
]

const CI = [
  { file: 'ci.yml', desc: '푸시마다 Gradle 빌드와 백엔드·프론트 테스트를 돌려 깨진 커밋을 막는다' },
  { file: 'codeql.yml', desc: 'Java·TypeScript 소스를 정적 분석해 주입·역직렬화 같은 취약 패턴을 경보로 올린다.\n싱크 목록에 JdbcClient.sql을 더해 SQL 문자열 조립 자리를 잡는다' },
  { file: 'e2e.yml', desc: '실제 브라우저로 로그인부터 장바구니 담기까지 화면 흐름을 밟아 본다' },
  { file: 'dependabot-automerge.yml', desc: '검사를 통과한 의존성 갱신 PR을 사람 손 없이 합친다' },
  { file: 'dependency-submission.yml', desc: 'Gradle·npm 의존성 좌표를 main에서 GitHub 의존성 그래프에 올려 취약점 경보가 붙을 대상을 만든다' },
  { file: 'claude-review.yml', desc: 'PR 디프를 모델에 넣어 지적 목록을 코멘트로 남긴다' },
]

const STACK = [
  { k: '백엔드', v: 'Spring Boot 4.1 · Java 25 · Spring Security · JdbcClient' },
  { k: '프론트', v: 'Next.js · TypeScript · Vitest · Playwright' },
  { k: 'DB', v: 'PostgreSQL 17 · Redis 7 · Flyway · pg_stat_statements' },
  { k: '인프라', v: 'Docker Compose · Testcontainers · Kafka · MinIO · GitHub Actions' },
]

// ISR 15분. 빌드 때 한 번 받고, 이후 15분마다 백그라운드로 다시 받는다
export const revalidate = 900

export default async function ProjectShop() {
  const { metrics, recent } = await getShopStats()

  const rows = metrics
    ? [
        { n: String(metrics.commits), label: '커밋' },
        { n: String(metrics.adr), label: '설계 기록(ADR)' },
        { n: String(metrics.migrations), label: 'Flyway 마이그레이션' },
        { n: String(metrics.docs), label: '기술 문서' },
        { n: String(metrics.tests), label: '테스트 파일' },
        { n: String(metrics.workflows), label: 'CI 워크플로' },
      ]
    : FALLBACK

  const metricsLabel = metrics
    ? `저장소 실측 · GitHub · ${metrics.pushedAt}`
    : '저장소 실측 · 2026-09 기준'

  return (
    <div style={{ paddingTop: '20px', maxWidth: '860px' }}>

      {/* ── 헤더 ───────────────────────────────────── */}
      <div className="mono" style={{ fontSize: '11px', color: 'var(--accent)', letterSpacing: '2px', marginBottom: '14px' }}>
        설계 기록 · 배포본 공개
      </div>

      <h1 className="display display-xl" style={{ lineHeight: 0.95, marginBottom: '24px' }}>
        <span style={{ color: 'var(--text)' }}>PROJECT</span><br />
        <span style={{ color: 'var(--accent)' }}>SHOP</span>
      </h1>

      <p style={{ ...S.body, fontSize: '15px', marginBottom: '12px' }}>
        멀티 셀러 쇼핑몰. 판매자가 여럿 입점하고, 고객이 사고, 관리자가 관리한다.
      </p>
      <p style={{ ...S.body, fontSize: '15px', marginBottom: '32px' }}>
        <span style={{ color: 'var(--text)', fontWeight: 700 }}>목표는 SI 현장에서는 못 해 본 설계 결정을 끝까지 끌고 가 검증하는 것</span>이다.<br />
        권한을 코드가 아니라 데이터로 두기, 세션 쿠키와 JWT 사이에서 고르기,<br />
        스키마를 코드처럼 이력 관리하기 등의 이력이 적혀있다.
      </p>

      <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '56px' }}>
        <a href={REPO_SHOP} target="_blank" rel="noopener noreferrer" style={{
          display: 'inline-flex', alignItems: 'center', gap: '8px',
          padding: '11px 20px', borderRadius: '6px', textDecoration: 'none',
          background: 'var(--accent)', color: 'var(--bg)',
          fontSize: '13px', fontWeight: 700, letterSpacing: '1px',
          fontFamily: 'var(--font-mono), monospace',
        }}>
          ⌥ GITHUB 저장소 →
        </a>
        <a href={DEMO_SHOP} target="_blank" rel="noopener noreferrer" style={{
          display: 'inline-flex', alignItems: 'center', gap: '8px',
          padding: '11px 20px', borderRadius: '6px', textDecoration: 'none',
          background: 'transparent', color: 'var(--accent)',
          border: '1px solid var(--accent)',
          fontSize: '13px', fontWeight: 700, letterSpacing: '1px',
          fontFamily: 'var(--font-mono), monospace',
        }}>
          ▶ 배포본 상품 목록 →
        </a>
        <a href={`${REPO_SHOP}/tree/main/doc/adr`} target="_blank" rel="noopener noreferrer" style={{
          display: 'inline-flex', alignItems: 'center', gap: '8px',
          padding: '11px 20px', borderRadius: '6px', textDecoration: 'none',
          background: 'transparent', color: 'var(--accent3)',
          border: '1px solid var(--border)',
          fontSize: '13px', fontWeight: 700, letterSpacing: '1px',
          fontFamily: 'var(--font-mono), monospace',
        }}>
          ◧ 설계 기록 {metrics?.adr ?? 11}건 →
        </a>
      </div>

      {/* ── 숫자 ───────────────────────────────────── */}
      <Section label={metricsLabel}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
          gap: '10px',
        }}>
          {rows.map(m => (
            <div key={m.label} style={{ ...S.card, padding: '16px 18px' }}>
              <div className="display" style={{ fontSize: '30px', color: 'var(--accent3)', lineHeight: 1 }}>{m.n}</div>
              <div className="mono" style={{ fontSize: '10px', color: 'var(--muted)', letterSpacing: '1px', marginTop: '8px' }}>{m.label}</div>
            </div>
          ))}
        </div>
      </Section>

      {/* ── 지금 상황 ───────────────────────────────── */}
      {recent && (
        <Section label="지금 상황 · 최근 커밋 5">
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1px', background: 'var(--border)', border: '1px solid var(--border)', borderRadius: '8px', overflow: 'hidden' }}>
            {recent.map(c => (
              <a key={c.sha} href={c.url} target="_blank" rel="noopener noreferrer" style={{
                background: 'var(--surface2)', padding: '12px 18px', textDecoration: 'none',
                display: 'flex', gap: '14px', alignItems: 'baseline', flexWrap: 'wrap',
              }}>
                <span className="mono" style={{ fontSize: '11px', color: 'var(--accent3)' }}>{c.date}</span>
                <span style={{ fontSize: '13px', color: 'var(--muted)', flex: 1, minWidth: '220px' }}>{c.message}</span>
                <span className="mono" style={{ fontSize: '11px', color: 'var(--accent)' }}>{c.sha}</span>
              </a>
            ))}
          </div>
        </Section>
      )}

      {/* ── 화면 ───────────────────────────────────── */}
      <Section label="00 · 무엇이 돌아가나">
        <p style={{ ...S.body, marginBottom: '18px' }}>
          배포본에서 찍은 화면이다. 위 버튼으로 같은 주소를 직접 연다.
        </p>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(320px, 100%), 1fr))', gap: '18px' }}>
          {SHOTS.map(shot => (
            <Shot key={shot.src} src={shot.src} alt={shot.alt} w={shot.w} h={shot.h} cap={shot.cap} />
          ))}
        </div>
      </Section>

      {/* ── 한 줄기 ───────────────────────────────── */}
      <Section label="01 · 한 줄기로 따라가기">
        <p style={{ ...S.body, marginBottom: '18px' }}>
          요건 하나가 법 조항에서 DB 제약·테스트·CI 까지 어떻게 이어지는지 따라간다.
        </p>
        <div style={{ marginBottom: '18px' }}>
          <Figure src="/images/ps-law-chain.svg" alt="법 조문 하나가 요건표를 거쳐 DB check 제약으로 내려가는 흐름" />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {TRACE.map((t, i) => (
            <div key={t.step}>
              <div style={S.card}>
                <div className="mono" style={{ fontSize: '10px', color: 'var(--accent2)', letterSpacing: '2px', marginBottom: '8px' }}>
                  {String(i + 1).padStart(2, '0')} · {t.step}
                </div>
                <p style={{ ...S.body, margin: '0 0 12px' }}>{t.body}</p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {t.links.map(l => (
                    <a key={l} href={`${REPO_SHOP}/blob/main/${l}`} target="_blank" rel="noopener noreferrer" className="mono tap"
                      style={{ fontSize: '11px', color: 'var(--accent)', textDecoration: 'none', wordBreak: 'break-all' }}>
                      {l} →
                    </a>
                  ))}
                </div>
              </div>
              {i < TRACE.length - 1 && (
                <div className="mono" style={{ color: 'var(--accent3)', fontSize: '14px', textAlign: 'center', padding: '6px 0' }}>↓</div>
              )}
            </div>
          ))}
        </div>
        <p style={{ ...S.body, fontSize: '13px', marginTop: '16px' }}>
          요건 40개가 전부 이 모양으로 이어지지는 않는다 —<br />
          아래 05 의 숫자가 그 구멍이다.
        </p>
      </Section>

      {/* ── 주제 선정 ───────────────────────────────── */}
      <Section label="02 · 왜 이 주제를 골랐나">
        <div style={S.card}>
          <p style={{ ...S.body, marginBottom: '14px' }}>
            실무에서 관리자와 사용자 구분이 코드 여기저기 흩어져 유지보수가 힘들어지는 걸 겪었다.<br />
            그게 어디서부터 잘못되는지 정면으로 다루려고 멀티 셀러 구조를 골랐다.<br />
            판매자가 여럿이어야 &ldquo;자기 상품만 수정&rdquo; 같은 행 단위 권한이 필요해지고,<br />
            실무에서 지저분해지는 지점이 정확히 거기다.
          </p>
          <p style={{ ...S.body, margin: 0 }}>
            그래서 판단이 갈릴 때는 기능을 늘리는 쪽이 아니라 구조가 드러나는 쪽을 고른다.<br />
            할인쿠폰 10종류보다 권한 판정 한 군데를 제대로 만드는 게 이 프로젝트의 목적에 맞다.
          </p>
        </div>
      </Section>

      {/* ── 설계 결정 ───────────────────────────────── */}
      <Section label="03 · 무엇을 저울질해서 정했나">
        <p style={{ ...S.body, marginBottom: '18px' }}>
          갈림길마다 고른 쪽과 버린 쪽, 그 근거를 ADR 파일로 저장소에 남긴다.<br />
          아래는 그중 넷이다.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {DECISIONS.map(d => (
            <div key={d.id} style={S.card}>
              <div className="mono" style={{ fontSize: '10px', color: 'var(--accent2)', letterSpacing: '2px', marginBottom: '8px' }}>{d.id}</div>
              <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text)', marginBottom: '10px' }}>{d.title}</div>
              <p style={{ ...S.body, margin: 0 }}>{d.body}</p>
              {d.image && (
                <div style={{ marginTop: '14px' }}>
                  <Figure src={d.image.src} alt={d.image.alt} />
                </div>
              )}
            </div>
          ))}
        </div>

        <div style={{ marginTop: '18px' }}>
          <div className="mono" style={{ fontSize: '10px', color: 'var(--accent2)', letterSpacing: '2px', marginBottom: '10px' }}>
            ADR-0003 이 화면에서 갈리는 자리
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            {ROLE_SHOTS.map(shot => (
              <Shot key={shot.src} src={shot.src} alt={shot.alt} w={shot.w} h={shot.h} cap={shot.cap} />
            ))}
          </div>
          <p style={{ ...S.body, fontSize: '13px', marginTop: '14px' }}>
            메뉴가 줄어드는 것은 눈에 보이는 쪽이고,<br />
            주소를 직접 쳤을 때 막는 판정은 role_permission 행이 낸다.
          </p>
        </div>
      </Section>

      {/* ── 작업 방식 ───────────────────────────────── */}
      <Section label="04 · 어떻게 굴리나">
        <div style={S.card}>
          <p style={{ ...S.body, marginBottom: '14px' }}>
            세션이 끊기는 걸 전제로 진행 상태를 전부 파일에 두고, 청크 하나를 커밋 하나로 친다.
          </p>
          <p style={{ ...S.body, marginBottom: '14px' }}>
            값 하나를 타입·스키마·DB 제약·앱 검증 중 어느 층에서 막을지를 코드보다 먼저 정한다.
          </p>
          <div style={{ marginBottom: '18px' }}>
            <Figure src="/images/ps-layers.svg" alt="문서·테스트·앱 검증·DB 제약·타입 다섯 층을 아래로 갈수록 일찍 막히는 순으로 늘어놓은 그림" />
          </div>
          <a href="/study/ai-native-notes" className="mono" style={{ fontSize: '12px', color: 'var(--accent)', letterSpacing: '1px', textDecoration: 'none' }}>
            AI 로 굴리는 방법은 학습 노트 「LLM · AI-Native」에 →
          </a>
        </div>
      </Section>

      {/* ── 검증 ───────────────────────────────────── */}
      <Section label="05 · 무엇이 자동으로 잡나">
        <div style={{ marginBottom: '14px' }}>
          <Figure src="/images/ps-pipeline.svg" alt="청크 하나가 PR 하나가 되고 검사 넷을 지나 합쳐지는 파이프라인" />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1px', background: 'var(--border)', border: '1px solid var(--border)', borderRadius: '8px', overflow: 'hidden' }}>
          {CI.map(c => (
            <div key={c.file} style={{ background: 'var(--surface2)', padding: '16px 20px' }}>
              <div className="mono" style={{ fontSize: '12px', color: 'var(--accent3)', marginBottom: '6px' }}>{c.file}</div>
              <div style={{ ...S.body, fontSize: '13px' }}>{c.desc}</div>
            </div>
          ))}
        </div>

        <div style={{ ...S.card, marginTop: '10px', borderColor: 'rgba(255,107,53,0.3)' }}>
          <div className="mono" style={{ fontSize: '10px', color: 'var(--accent2)', letterSpacing: '2px', marginBottom: '8px' }}>아직 안 닫힌 것</div>
          <p style={{ ...S.body, margin: 0 }}>
            <code style={{ color: 'var(--text)' }}>req-coverage.sh</code>가 요구사항 40개 중<br />
            테스트가 한 번도 부르지 않는 14개를 매번 세서 숫자로 남긴다.<br />
            그 14개가 검증 구멍인지 애초에 테스트할 수 없는 제약인지는 아직 안 갈랐다.<br />
            숨기지 않고 숫자로 들고 다니는 쪽을 골랐다.
          </p>
        </div>
      </Section>

      {/* ── 스택 ───────────────────────────────────── */}
      <Section label="06 · 스택">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {STACK.map(s => (
            <div key={s.k} style={{ ...S.card, display: 'flex', gap: '18px', alignItems: 'baseline', flexWrap: 'wrap' }}>
              <div className="mono" style={{ fontSize: '11px', color: 'var(--accent)', letterSpacing: '2px', minWidth: '60px' }}>{s.k}</div>
              <div style={{ ...S.body, fontSize: '13px' }}>{s.v}</div>
            </div>
          ))}
        </div>
        <p style={{ ...S.body, fontSize: '13px', marginTop: '16px' }}>
          결제는 PG를 흉내내는 모의 모듈로 둔다.<br />
          상태 전이와 멱등성 설계는 그대로 연습되고, 사업자등록과 심사를 안 거친다.
        </p>
      </Section>

      {/* ── 닫는 링크 ───────────────────────────────── */}
      <div style={{
        ...S.card,
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        gap: '16px', flexWrap: 'wrap',
      }}>
        <div>
          <div style={{ fontSize: '15px', fontWeight: 700, color: 'var(--text)', marginBottom: '6px' }}>
            ProjectShop Github
          </div>
          <div className="mono" style={{ fontSize: '11px', color: 'var(--muted)' }}>
            Apache-2.0
          </div>
        </div>
        <a href={REPO_SHOP} target="_blank" rel="noopener noreferrer" style={{
          color: 'var(--accent)', fontSize: '13px', fontWeight: 700,
          textDecoration: 'none', fontFamily: 'var(--font-mono), monospace', letterSpacing: '1px',
        }}>
          ejg93/ProjectShop →
        </a>
      </div>
    </div>
  )
}
