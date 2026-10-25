'use client'
import { useEffect, useMemo, useState } from 'react'
import { CHEONGYAK } from '@/lib/api'
import { useStoredJson } from '@/lib/clientStore'
import type { Cmpet, CollectRun, Cond, Filters, HouseTypeView, Judgement, NoticeView, Reason, Rules, Score, Slot, Status } from './types'

// 청약 공고 판정. 조건·검색 칩은 이 브라우저의 슬롯 다섯 칸에만 두고, 판정은 서버 규칙 엔진 응답을 그대로 그린다.
// 화면에서 판정 규칙을 다시 계산하지 않는다 — 규칙은 백엔드 rules/ 한 곳에 있다.

const SIDOS = ['서울', '부산', '인천', '대구', '광주', '대전', '울산', '세종', '경기', '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주']
const EMPTY_SLOTS: (Slot | null)[] = [null, null, null, null, null]
const DEFAULT_COND: Cond = {
  sido: '서울', birth: '1991-05-10', marriage: '', houses: 0, homelessSince: '', deps: 1, acct: 62, pay: 60,
  deposit: 400, spAcct: 0, resMonths: 30, city: '', cityMonths: 0, householder: true, won5: false, won2: false,
}
const DEFAULT_FILTERS: Filters = { area: 'capital', kind: 'all', period: 90, rcpt: 'live', size: 'all', result: 'all' }
const RANK: Record<Status, number> = { RANK1: 3, RANK2: 2, INELIGIBLE: 1, NOT_APPLICABLE: 0 }
const AREA_LABEL: Record<Filters['area'], string> = { capital: '수도권', 서울: '서울', 경기: '경기', 인천: '인천', all: '전국' }

type Chip<K extends keyof Filters> = { key: K; label: string; options: [Filters[K], string][] }
const CHIPS: [Chip<'area'>, Chip<'kind'>, Chip<'period'>, Chip<'rcpt'>, Chip<'size'>, Chip<'result'>] = [
  { key: 'area', label: '지역', options: [['capital', '수도권'], ['서울', '서울'], ['경기', '경기'], ['인천', '인천'], ['all', '전국']] },
  { key: 'kind', label: '종류', options: [['all', '전체'], ['APT01', 'APT 민영'], ['APT03', 'APT 국민'], ['REMNDR', '무순위'], ['etc', '기타']] },
  { key: 'period', label: '공고일', options: [[90, '최근 90일'], [180, '180일'], [365, '365일']] },
  { key: 'rcpt', label: '접수', options: [['live', '접수 예정·중'], ['all', '마감 포함']] },
  { key: 'size', label: '전용면적', options: [['all', '전체'], ['s', '60㎡ 이하'], ['m', '60~85㎡'], ['l', '85㎡ 초과']] },
  { key: 'result', label: '판정', options: [['all', '전체'], ['ok', '신청 가능만'], ['r1', '1순위만']] },
]

const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' })
const daysAgo = (n: number) => {
  const d = new Date(`${today()}T00:00:00`)
  d.setDate(d.getDate() - n)
  return d.toLocaleDateString('sv-SE')
}
const dayDiff = (a: string, b: string) => Math.round((new Date(a).getTime() - new Date(b).getTime()) / 864e5)
const key = (hmn: string, pno: string) => `${hmn}:${pno}`

function toApplicant(c: Cond) {
  return {
    residenceSido: c.sido, birthDate: c.birth || null, marriageDate: c.marriage || null,
    householdHomeless: c.houses === 0, homelessSince: c.homelessSince || null, householdHouseCount: c.houses,
    householder: c.householder, dependents: c.deps, accountMonths: c.acct, paymentCount: c.pay,
    depositManwon: c.deposit, savingsTotalManwon: 0, spouseAccountMonths: c.spAcct,
    wonWithin5y: c.won5, wonByPointsWithin2y: c.won2, residenceMonthsInSido: c.resMonths,
    residenceCity: c.sido === '경기' && c.city ? c.city : null, residenceMonthsInCity: c.cityMonths,
  }
}

/** 슬롯 단추 아랫줄 — 시도 · 통장 개월 · 검색 지역 */
function slotSummary(slot: Slot) {
  const { sido, acct } = slot.cond
  const area = AREA_LABEL[slot.filters.area] ?? slot.filters.area
  return [sido, `통장 ${acct}개월`, area].join(' · ')
}

function statusLabel(s: Status, kind: string) {
  if (s === 'RANK1') return kind === 'REMNDR' ? '신청 가능' : '1순위 가능'
  return { RANK2: '2순위', INELIGIBLE: '신청 불가', NOT_APPLICABLE: '판정 대상 아님' }[s]
}

function sizeOk(area: number | null, size: Filters['size']) {
  if (size === 'all') return true
  if (area == null) return false
  return size === 's' ? area <= 60 : size === 'm' ? area > 60 && area <= 85 : area > 85
}

function kindOk(n: NoticeView, kind: Filters['kind']) {
  if (kind === 'all') return true
  if (kind === 'APT01') return n.kind === 'APT' && n.houseDtlSecd === '01'
  if (kind === 'APT03') return n.kind === 'APT' && n.houseDtlSecd === '03'
  if (kind === 'REMNDR') return n.kind === 'REMNDR'
  return n.kind !== 'APT' && n.kind !== 'REMNDR'
}

const NEW_HOURS = 36

/**
 * 처음 수집된 지 NEW_HOURS 안이고 공고일도 사흘 안. 공고일을 같이 보는 건 첫 백필 때문이다 —
 * 1년치를 한꺼번에 받은 날에는 모든 공고가 「처음 본 지 하루 안」 이다. firstSeenAt 은 한국 시간이다
 */
function isNew(n: NoticeView, nowMs: number) {
  if (!n.firstSeenAt || !n.rcritPblancDe || nowMs <= 0) return false
  const seen = nowMs - Date.parse(`${n.firstSeenAt.slice(0, 19)}+09:00`) < NEW_HOURS * 36e5
  return seen && nowMs - Date.parse(`${n.rcritPblancDe}T00:00:00+09:00`) < 3 * 864e5
}

type Mkt = { text: string; color: string; ratio: number | null }

/** 최고 분양가 ÷ 실거래 시세 중앙값. 0.90 이하 저렴, 1.10 초과 비쌈, 시세가 없으면 비교 불가 */
function mktLabel(top: number | null, median: number | null): Mkt {
  if (!top || !median) return { text: '비교 불가', color: 'var(--muted)', ratio: null }
  const ratio = top / median
  if (ratio <= 0.9) return { text: '시세 대비 저렴', color: 'var(--accent)', ratio }
  if (ratio > 1.1) return { text: '시세 대비 비쌈', color: 'var(--accent2)', ratio }
  return { text: '시세 수준', color: 'var(--accent3)', ratio }
}

/** 카드 요약에 쓸 주택형: 전용 84㎡ 에 가장 가까운 것 */
function representative(types: HouseTypeView[]) {
  return types.filter((t) => t.exclusiveArea != null)
    .reduce<HouseTypeView | null>((m, t) => (!m || Math.abs((t.exclusiveArea ?? 0) - 84) < Math.abs((m.exclusiveArea ?? 0) - 84) ? t : m), null)
}

const areaOf = (houseTy: string | null) => (houseTy ? parseFloat(houseTy) : NaN)

/** 주택형 이름이 API 마다 조금 달라(084.9700A · 084A) 앞 전용면적 숫자로도 맞춘다 */
function sameType(a: string | null, b: string | null) {
  if (!a || !b) return false
  return a === b || Math.floor(areaOf(a)) === Math.floor(areaOf(b))
}

function rateOf(t: HouseTypeView, rows: Cmpet[]) {
  const r = rows.find((c) => sameType(c.houseTy, t.houseTy) && (c.rankCode === 0 || (c.rankCode === 1 && c.resideNm.startsWith('해당'))))
  return !r ? '—' : r.cmpetRate != null ? `${r.cmpetRate}:1` : r.cmpetText ?? '—'
}

function minScoreOf(t: HouseTypeView, rows: Score[]) {
  return rows.find((c) => sameType(c.houseTy, t.houseTy) && c.resideNm.startsWith('해당'))?.minScore ?? '—'
}

function docTags(n: NoticeView) {
  const d = n.doc
  if (!d.status) return []
  if (d.status !== 'OK') return ['공고문 첨부 없음']
  const t: string[] = []
  if (d.remndrArea) t.push(`자격 ${d.remndrArea} 거주${d.remndrHomeless ? ' 무주택' : ''}`)
  if (d.crspCity) t.push(`해당지역 ${d.crspCity}${d.crspMonths ? ` ${d.crspMonths / 12}년↑` : ''}`)
  if (d.resPriArea && d.resPriMonths) t.push(`${d.resPriArea} ${d.resPriMonths / 12}년↑ 우선`)
  if (d.rewinYears != null) t.push(d.rewinYears ? `재당첨 ${d.rewinYears}년` : '재당첨 제한 없음')
  if (n.kind === 'REMNDR' && d.regulation) t.push(d.regulation)
  return t
}

const S = {
  section: { background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: '10px', padding: '18px 18px', marginTop: '18px' },
  h2: { fontFamily: 'var(--font-mono, monospace)', fontSize: '13px', fontWeight: 600, color: 'var(--muted)', margin: '0 0 12px' },
  input: { font: 'inherit', fontSize: '14px', padding: '6px 8px', border: '1px solid var(--border)', borderRadius: '6px', background: 'var(--surface)', color: 'var(--text)', width: '100%' },
  label: { display: 'flex', flexDirection: 'column' as const, gap: '3px', fontSize: '12px', color: 'var(--muted)' },
  mono: { fontFamily: 'var(--font-mono, monospace)' },
  th: { fontFamily: 'var(--font-mono, monospace)', fontSize: '11px', color: 'var(--muted)', fontWeight: 500, textAlign: 'left' as const, padding: '5px 8px', borderBottom: '1px solid var(--border)', whiteSpace: 'nowrap' as const },
  td: { fontSize: '12px', padding: '5px 8px', borderBottom: '1px solid var(--border)', whiteSpace: 'nowrap' as const },
  tag: { display: 'inline-block', fontFamily: 'var(--font-mono, monospace)', fontSize: '11px', padding: '0 5px', borderRadius: '4px', marginRight: '4px', border: '1px solid var(--border)' },
}

function chipStyle(on: boolean) {
  return {
    padding: '5px 11px', borderRadius: '6px', fontSize: '12px', cursor: 'pointer',
    background: on ? 'var(--accent)' : 'var(--surface2)', color: on ? 'var(--on-accent)' : 'var(--text)',
    border: '1px solid ' + (on ? 'var(--accent)' : 'var(--border)'),
  }
}

function badgeStyle(s: Status | undefined) {
  // border 는 축약형 하나로만 쓴다 — borderColor 를 섞으면 다시 그릴 때 리액트가 경고한다
  const base = { display: 'inline-block', padding: '3px 8px', borderRadius: '5px', fontSize: '12px', fontWeight: 600 }
  if (s === 'RANK1') return { ...base, background: 'var(--accent)', color: 'var(--on-accent)', border: '1px solid var(--accent)' }
  if (s === 'RANK2') return { ...base, background: 'var(--surface2)', color: 'var(--text)', border: '1px solid var(--border)' }
  if (s === 'INELIGIBLE') return { ...base, color: 'var(--accent2)', border: '1px solid var(--accent2)' }
  return { ...base, color: 'var(--muted)', border: '1px solid var(--border)' }
}

const ICON: Record<Reason['level'], string> = { PASS: '✓', FAIL: '✗', CHECK: '?', INFO: '·' }
const ICON_COLOR: Record<Reason['level'], string> = { PASS: 'var(--accent)', FAIL: 'var(--accent2)', CHECK: 'var(--accent3)', INFO: 'var(--muted)' }

function ReasonList({ items }: { items: Reason[] }) {
  return (
    <ul style={{ listStyle: 'none', padding: 0, margin: '6px 0 0' }}>
      {items.map((r, i) => (
        <li key={i} style={{ display: 'grid', gridTemplateColumns: '18px 1fr auto', gap: '6px', padding: '4px 0', borderBottom: '1px dotted var(--border)', fontSize: '13px' }}>
          <span aria-hidden style={{ ...S.mono, fontWeight: 700, color: ICON_COLOR[r.level], textAlign: 'center' }}>{ICON[r.level]}</span>
          <span>{r.text}</span>
          <span style={{ ...S.mono, fontSize: '10px', color: 'var(--muted)' }}>{r.basis}</span>
        </li>
      ))}
    </ul>
  )
}

export default function CheongyakPage() {
  const [slotsRaw, setSlots] = useStoredJson<(Slot | null)[]>('cheongyak-slots', EMPTY_SLOTS)
  const [lastRaw, setLast] = useStoredJson<number>('cheongyak-last-slot', 0)
  const slots = Array.isArray(slotsRaw) && slotsRaw.length === 5 ? slotsRaw : EMPTY_SLOTS
  const cur = Number.isInteger(lastRaw) && lastRaw >= 0 && lastRaw < 5 ? lastRaw : 0
  const slot = slots[cur]
  const cond = useMemo<Cond>(() => ({ ...DEFAULT_COND, ...(slot?.cond ?? {}) }), [slot])
  const filters = useMemo<Filters>(() => ({ ...DEFAULT_FILTERS, ...(slot?.filters ?? {}) }), [slot])

  const save = (patch: Partial<Slot>) => {
    const next = [...slots]
    next[cur] = { name: slot?.name ?? '', cond, filters, ...patch, savedAt: new Date().toISOString() }
    setSlots(next)
  }
  const setCond = <K extends keyof Cond>(k: K, v: Cond[K]) => save({ cond: { ...cond, [k]: v } })
  const setFilter = <K extends keyof Filters>(k: K, v: Filters[K]) => save({ filters: { ...filters, [k]: v } })
  const clearSlot = () => {
    const next = [...slots]
    next[cur] = null
    setSlots(next)
  }

  // ── 공고 목록: 지역·공고일이 바뀔 때만 다시 받는다. 나머지 칩은 화면에서 거른다
  const from = daysAgo(filters.period)
  const noticeQuery = `area=${encodeURIComponent(filters.area)}&from=${from}`
  const [noticeState, setNoticeState] = useState<{ q: string; list: NoticeView[] | null; error: string | null; at: number }>({ q: '', list: null, error: null, at: 0 })
  useEffect(() => {
    let alive = true
    fetch(`${CHEONGYAK}/notices?${noticeQuery}`)
      .then(async (r) => {
        const d = await r.json().catch(() => ({}))
        if (!r.ok) throw new Error(d.message ?? `서버 오류: ${r.status}`)
        return d
      })
      .then((d) => alive && setNoticeState({ q: noticeQuery, list: d.notices ?? [], error: null, at: Date.now() }))
      .catch((e: Error) => alive && setNoticeState({ q: noticeQuery, list: null, error: e.message, at: Date.now() }))
    return () => {
      alive = false
    }
  }, [noticeQuery])
  const noticesLoading = noticeState.q !== noticeQuery
  const notices = noticesLoading ? null : noticeState.list

  // ── 판정: 조건이나 목록이 바뀌면 0.3초 뒤 한 번 묻는다
  const judgeBody = useMemo(
    () => (notices ? JSON.stringify({ applicant: toApplicant(cond), notices: notices.map((n) => ({ houseManageNo: n.houseManageNo, pblancNo: n.pblancNo })) }) : null),
    [notices, cond],
  )
  const [judgeState, setJudgeState] = useState<{ body: string | null; results: Judgement[]; error: string | null }>({ body: null, results: [], error: null })
  useEffect(() => {
    if (!judgeBody) return
    const t = setTimeout(() => {
      fetch(`${CHEONGYAK}/judge`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: judgeBody })
        .then(async (r) => {
          const d = await r.json().catch(() => ({}))
          if (!r.ok) throw new Error(d.message ?? `서버 오류: ${r.status}`)
          return d
        })
        .then((d) => setJudgeState({ body: judgeBody, results: d.results ?? [], error: null }))
        .catch((e: Error) => setJudgeState({ body: judgeBody, results: [], error: e.message }))
    }, 300)
    return () => clearTimeout(t)
  }, [judgeBody])
  const judgeFresh = judgeState.body === judgeBody
  const byNotice = useMemo(() => {
    const m = new Map<string, Judgement[]>()
    for (const j of judgeState.results) {
      const k = key(j.houseManageNo, j.pblancNo)
      m.set(k, [...(m.get(k) ?? []), j])
    }
    return m
  }, [judgeState.results])

  // ── 수집 이력·규칙 표: 한 번만
  const [runs, setRuns] = useState<{ list: CollectRun[] | null; error: string | null; at: number }>({ list: null, error: null, at: 0 })
  const [rules, setRules] = useState<Rules | null>(null)
  useEffect(() => {
    fetch(`${CHEONGYAK}/runs?limit=10`)
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`서버 오류: ${r.status}`))))
      .then((d) => setRuns({ list: d.runs ?? [], error: null, at: Date.now() }))
      .catch((e: Error) => setRuns({ list: null, error: e.message, at: Date.now() }))
    fetch(`${CHEONGYAK}/rules`)
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`서버 오류: ${r.status}`))))
      .then((d) => setRules(d))
      .catch(() => setRules(null))
  }, [])

  const [open, setOpen] = useState<Set<string>>(() => new Set())
  const [picked, setPicked] = useState<Record<string, number>>({})
  const toggle = (k: string) => {
    const next = new Set(open)
    if (next.has(k)) next.delete(k)
    else next.add(k)
    setOpen(next)
  }

  // ── 카드 행: 종류·면적·접수·판정으로 거르고, 판정 좋은 순 → 접수 빠른 순
  const now = today()
  const rows = (notices ?? [])
    .filter((n) => kindOk(n, filters.kind))
    .map((n) => {
      const js = judgeFresh ? byNotice.get(key(n.houseManageNo, n.pblancNo)) ?? [] : []
      const types = n.houseTypes.length ? n.houseTypes.filter((t) => sizeOk(t.exclusiveArea, filters.size)) : []
      const judgeOf = (t: HouseTypeView | null) => js.find((j) => t && j.modelNo === t.modelNo) ?? js.find((j) => j.modelNo == null) ?? js[0]
      const visible = (types.length ? types.map(judgeOf) : [judgeOf(null)]).filter((j): j is Judgement => !!j)
      const best = visible.reduce<Judgement | undefined>((m, j) => (!m || RANK[j.status] > RANK[m.status] ? j : m), undefined)
      const begin = best?.receipt.rank1Begin ?? n.rceptBgnde ?? n.rnk1CrspBgnde
      const end = best?.receipt.rank1End ?? n.rceptEndde ?? begin
      return { n, types, judgeOf, best, begin, end, hasCheck: visible.some((j) => j.hasCheck) }
    })
    .filter((r) => filters.size === 'all' || r.types.length > 0)
  const shown = rows
    .filter((r) => filters.rcpt === 'all' || !r.end || r.end >= now)
    .filter((r) => filters.result === 'all' || (r.best && (filters.result === 'r1' ? r.best.status === 'RANK1' : r.best.status === 'RANK1' || r.best.status === 'RANK2')))
    .sort((a, b) => (b.best ? RANK[b.best.status] : -1) - (a.best ? RANK[a.best.status] : -1) || (a.begin ?? '9').localeCompare(b.begin ?? '9'))
  const count = (s: Status) => shown.filter((r) => r.best?.status === s).length
  const latestScore = judgeState.results.find((j) => j.score)?.score ?? null

  const lastOk = runs.list?.find((r) => r.status === 'OK')
  const hoursAgo = lastOk?.finishedAt ? Math.max(0, Math.round((runs.at - new Date(lastOk.finishedAt).getTime()) / 36e5)) : null

  const field = (id: keyof Cond, label: string, type: 'number' | 'date') => (
    <label style={S.label} htmlFor={`c-${id}`}>
      {label}
      <input
        id={`c-${id}`}
        type={type}
        min={type === 'number' ? 0 : undefined}
        style={S.input}
        value={String(cond[id] ?? '')}
        onChange={(e) => setCond(id, (type === 'number' ? Number(e.target.value) || 0 : e.target.value) as never)}
      />
    </label>
  )

  return (
    <div style={{ maxWidth: '980px', margin: '0 auto', paddingTop: '60px', paddingBottom: '80px' }}>
      <div className="mono" style={{ fontSize: '11px', color: 'var(--accent)', letterSpacing: '4px', marginBottom: '12px' }}>
        EXPERIMENT_04
      </div>
      <h1 style={{ fontSize: '28px', margin: '0 0 4px' }}>청약 공고 판정</h1>
      <p className="mono" style={{ fontSize: '12px', color: 'var(--muted)', margin: 0 }}>
        청약홈 공개 API → Postgres → 규칙 엔진. 조건은 이 브라우저에만 저장된다
      </p>

      {/* ① 슬롯 + 내 조건 */}
      <section style={S.section}>
        <h2 style={S.h2}>① 내 조건 — 바꾸면 지금 슬롯에 저장되고 아래 공고가 다시 판정된다</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: '8px', marginBottom: '10px' }}>
          {slots.map((s, i) => (
            <button
              key={i}
              type="button"
              onClick={() => setLast(i)}
              aria-pressed={i === cur}
              style={{
                textAlign: 'left', padding: i === cur ? '7px 9px' : '8px 10px', borderRadius: '8px', cursor: 'pointer',
                background: i === cur ? 'var(--surface)' : 'var(--surface2)', color: 'var(--text)',
                border: i === cur ? '2px solid var(--accent)' : '1px solid var(--border)', fontSize: '12px', minWidth: 0,
              }}
            >
              <span style={{ ...S.mono, display: 'block', fontSize: '11px', color: i === cur ? 'var(--accent)' : 'var(--muted)' }}>SLOT {i + 1}</span>
              <span style={{ display: 'block', fontWeight: s ? 600 : 400, color: s ? 'var(--text)' : 'var(--muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {s ? s.name || '이름 없음' : '빈 슬롯'}
              </span>
              <span style={{ display: 'block', fontSize: '11px', color: 'var(--muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {s ? slotSummary(s) : '누르면 기본값으로 시작'}
              </span>
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap', paddingBottom: '12px', marginBottom: '14px', borderBottom: '1px dashed var(--border)' }}>
          <label htmlFor="slot-name" style={{ fontSize: '13px', display: 'flex', gap: '6px', alignItems: 'center' }}>
            이름
            <input id="slot-name" maxLength={12} placeholder="예: 우리 집" value={slot?.name ?? ''} onChange={(e) => save({ name: e.target.value })} style={{ ...S.input, width: '140px' }} />
          </label>
          <span style={{ fontSize: '11px', color: 'var(--muted)' }}>
            {slot ? `슬롯 ${cur + 1} 에 자동 저장 중` : `슬롯 ${cur + 1} 비어 있음 — 조건을 바꾸면 여기에 저장된다`}
          </span>
          <button type="button" onClick={clearSlot} style={chipStyle(false)}>이 슬롯 비우기</button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))', gap: '10px 16px' }}>
          <label style={S.label} htmlFor="c-sido">
            거주 시도
            <select id="c-sido" style={S.input} value={cond.sido} onChange={(e) => setCond('sido', e.target.value)}>
              {SIDOS.map((s) => <option key={s}>{s}</option>)}
            </select>
          </label>
          {field('birth', '생년월일', 'date')}
          {field('marriage', '혼인신고일 (없으면 비움)', 'date')}
          <label style={S.label} htmlFor="c-houses">
            세대 보유 주택 수
            <select id="c-houses" style={S.input} value={cond.houses} onChange={(e) => setCond('houses', Number(e.target.value))}>
              <option value={0}>0 (무주택)</option>
              <option value={1}>1</option>
              <option value={2}>2 이상</option>
            </select>
          </label>
          {field('homelessSince', '무주택 된 날 (집 판 적 있으면)', 'date')}
          {field('deps', '부양가족 수 (배우자 포함)', 'number')}
          {field('acct', '청약통장 가입 개월', 'number')}
          {field('pay', '납입 인정 횟수', 'number')}
          {field('deposit', '예치금 (만원)', 'number')}
          {field('spAcct', '배우자 통장 가입 개월', 'number')}
          {field('resMonths', '현 시도 연속 거주 개월', 'number')}
          {cond.sido === '경기' && (
            <>
              <label style={S.label} htmlFor="c-city">
                거주 시군 (경기만)
                <select id="c-city" style={S.input} value={cond.city} onChange={(e) => setCond('city', e.target.value)}>
                  <option value="">선택 안 함</option>
                  {(rules?.ggCities ?? []).map((c) => <option key={c} value={c}>{c}</option>)}
                </select>
              </label>
              {field('cityMonths', '현 시군 연속 거주 개월', 'number')}
            </>
          )}
        </div>
        <div style={{ display: 'flex', gap: '18px', flexWrap: 'wrap', marginTop: '12px', fontSize: '13px' }}>
          {([['householder', '세대주'], ['won5', '세대 내 5년 내 당첨자 있음'], ['won2', '2년 내 가점제 당첨']] as const).map(([k, l]) => (
            <label key={k} htmlFor={`c-${k}`} style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              <input id={`c-${k}`} type="checkbox" checked={cond[k]} onChange={(e) => setCond(k, e.target.checked)} /> {l}
            </label>
          ))}
        </div>
        {latestScore && (
          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', alignItems: 'center', marginTop: '14px', paddingTop: '12px', borderTop: '1px dashed var(--border)' }}>
            <span style={{ fontSize: '11px', color: 'var(--muted)' }}>가장 최근 공고일 기준 가점</span>
            {[
              [`${latestScore.total}`, '합계 / 84', true],
              [`${latestScore.homeless}`, `무주택 ${latestScore.homelessYears}년`, false],
              [`${latestScore.dependents}`, `부양 ${cond.deps}명`, false],
              [`${latestScore.account}`, latestScore.spouseBonus ? `통장 (배우자 +${latestScore.spouseBonus})` : '통장', false],
            ].map(([v, l, total]) => (
              <div key={String(l)} style={{ ...S.mono, fontSize: '12px', padding: '6px 12px', borderRadius: '8px', background: total ? 'var(--accent)' : 'var(--surface2)', color: total ? 'var(--on-accent)' : 'var(--text)' }}>
                <b style={{ display: 'block', fontSize: '16px' }}>{v}</b>{l}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* ② 검색 조건 */}
      <section style={S.section}>
        <h2 style={S.h2}>② 검색 조건</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {CHIPS.map((c) => (
            <div key={c.key} role="group" aria-label={c.label} style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', alignItems: 'center' }}>
              <span style={{ ...S.mono, width: '64px', fontSize: '11px', color: 'var(--muted)' }}>{c.label}</span>
              {c.options.map(([v, l]) => (
                <button key={String(v)} type="button" aria-pressed={filters[c.key] === v} style={chipStyle(filters[c.key] === v)}
                  onClick={() => setFilter(c.key, v as never)}>
                  {l}
                </button>
              ))}
            </div>
          ))}
        </div>
      </section>

      {/* ③ 공고 */}
      <section style={S.section}>
        <h2 style={S.h2}>③ 공고 — 행을 누르면 주택형별 판정과 근거가 펼쳐진다</h2>
        {noticesLoading && <div className="mono" style={{ fontSize: '12px', color: 'var(--muted)', padding: '16px 0' }}>◌ 공고 불러오는 중...</div>}
        {(noticeState.error || judgeState.error) && !noticesLoading && (
          <div className="mono" style={{ fontSize: '12px', color: 'var(--accent2, #ff6b6b)', padding: '14px', border: '1px solid var(--border)', borderRadius: '6px' }}>
            ERROR: {noticeState.error ?? judgeState.error}
          </div>
        )}
        {notices && (
          <div className="mono" style={{ fontSize: '12px', color: 'var(--muted)', margin: '4px 0 6px' }}>
            {shown.length}건 (조건 맞는 공고 {rows.length}건 중) · 1순위·신청 가능 {count('RANK1')} · 2순위 {count('RANK2')} · 불가 {count('INELIGIBLE')}
            {!judgeFresh && ' · 판정 중'}
          </div>
        )}
        {notices && shown.length === 0 && (
          <div style={{ padding: '28px', textAlign: 'center', color: 'var(--muted)', border: '1px dashed var(--border)', borderRadius: '8px' }}>
            조건에 맞는 공고가 없다. 검색 조건을 넓혀 본다
          </div>
        )}
        {shown.map(({ n, types, judgeOf, best, begin, end, hasCheck }) => {
          const k = key(n.houseManageNo, n.pblancNo)
          const isOpen = open.has(k)
          const rowTypes = types.length ? types : []
          const sel = Math.min(picked[k] ?? 0, Math.max(0, rowTypes.length - 1))
          const cur = rowTypes.length ? judgeOf(rowTypes[sel]) : judgeOf(null)
          const state = !begin ? '' : now < begin ? `접수 D-${dayDiff(begin, now)}` : end && now <= end ? '접수 중' : '마감'
          const genTotal = n.houseTypes.reduce((s, t) => s + (t.suplyHshldco ?? 0), 0)
          const rep84 = representative(n.houseTypes)
          const mkt = mktLabel(rep84?.topAmount ?? null, rep84?.mktMedian ?? null)
          return (
            <div key={k} style={{ border: '1px solid var(--border)', borderRadius: '8px', marginTop: '8px', overflow: 'hidden', background: 'var(--surface)' }}>
              <button type="button" onClick={() => toggle(k)} aria-expanded={isOpen}
                style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 12px', alignItems: 'center', width: '100%', padding: '12px 14px', background: 'transparent', border: 0, textAlign: 'left', cursor: 'pointer', color: 'var(--text)' }}>
                <span style={{ width: '112px', flexShrink: 0 }}>
                  <span style={badgeStyle(best?.status)}>{best ? statusLabel(best.status, n.kind) : '판정 중'}</span>
                  {hasCheck && <span style={{ ...S.mono, display: 'block', marginTop: '4px', fontSize: '11px', color: 'var(--accent2)' }}>● 공고 확인 필요</span>}
                </span>
                <span style={{ flex: '1 1 260px', minWidth: 0 }}>
                  <span style={{ display: 'block', fontWeight: 600 }}>
                    {n.houseNm}
                    {isNew(n, noticeState.at) && <span style={{ ...S.tag, marginLeft: '6px', color: 'var(--on-accent)', background: 'var(--accent)', border: 0 }}>NEW</span>}
                  </span>
                  <span style={{ display: 'block', fontSize: '12px', color: 'var(--muted)', marginTop: '2px' }}>
                    <span style={S.tag}>{n.kindLabel}</span>
                    {n.specltRdnAt === 'Y' && <span style={{ ...S.tag, color: 'var(--accent2)' }}>투기과열</span>}
                    {n.mdatTrgetAt === 'Y' && <span style={{ ...S.tag, color: 'var(--accent2)' }}>조정대상</span>}
                    {n.pubHousing && <span style={S.tag}>공공주택지구</span>}
                    {docTags(n).map((t) => <span key={t} style={{ ...S.tag, color: 'var(--accent3)', border: '1px solid var(--accent3)' }}>공고문 · {t}</span>)}
                  </span>
                  <span style={{ display: 'block', fontSize: '12px', color: 'var(--muted)', marginTop: '2px' }}>
                    {n.areaNm} · 공고 {n.rcritPblancDe?.slice(5)} · {n.houseTypes.length}개 주택형 · 일반 {genTotal}세대
                  </span>
                  {(n.resultStatus === 'OK' || n.refScore || mkt.ratio != null) && (
                    <span style={{ display: 'block', fontSize: '12px', color: 'var(--muted)', marginTop: '2px' }}>
                      {n.resultStatus === 'OK' && n.topRate != null && <span style={S.tag}>경쟁률 최고 {n.topRate}:1</span>}
                      {n.resultStatus === 'OK' && n.minScore != null && <span style={S.tag}>최저 당첨가점 {n.minScore}</span>}
                      {n.refScore && (
                        <span style={S.tag}>
                          {n.refScore.basis === 'LAWD' ? '이 시군구' : '이 시도'} 최근 1년 최저 당첨가점 중앙값 {n.refScore.median}
                          {latestScore ? ` · 내 가점 ${latestScore.total}` : ''}
                        </span>
                      )}
                      {mkt.ratio != null && (
                        <span style={{ ...S.tag, color: mkt.color, border: `1px solid ${mkt.color}` }}>
                          {Math.floor(rep84?.exclusiveArea ?? 0)}㎡ {mkt.text} {Math.round(mkt.ratio * 100)}%
                        </span>
                      )}
                    </span>
                  )}
                </span>
                <span style={{ ...S.mono, fontSize: '12px', textAlign: 'right', marginLeft: 'auto' }}>
                  <span style={{ display: 'block', fontWeight: 600, color: state === '마감' ? 'var(--muted)' : state === '접수 중' ? 'var(--accent)' : 'var(--accent3)' }}>{state}</span>
                  {n.kind === 'REMNDR' ? '접수' : `1순위 ${best?.receipt.label ?? ''}`} {begin?.slice(5)}{end && end !== begin ? `~${end.slice(5)}` : ''}
                </span>
              </button>
              {isOpen && (
                <div style={{ borderTop: '1px solid var(--border)', padding: '12px 14px', background: 'var(--surface2)' }}>
                  {rowTypes.length > 0 && (
                    <div style={{ overflowX: 'auto' }}>
                      <table style={{ borderCollapse: 'collapse', width: '100%' }}>
                        <thead>
                          <tr>
                            {['주택형', '전용㎡', '일반', '특공', '분양가(만원)', '시세(만원)', '시세 대비', '판정', '가점', '가점제 비율', '경쟁률', '최저 가점']
                              .map((h) => <th key={h} style={S.th}>{h}</th>)}
                          </tr>
                        </thead>
                        <tbody>
                          {rowTypes.map((t, i) => {
                            const j = judgeOf(t)
                            const ps = j?.lottery
                            return (
                              <tr key={t.modelNo} style={{ background: i === sel ? 'var(--surface)' : 'transparent' }}>
                                <td style={S.td}>
                                  <button type="button" onClick={() => setPicked({ ...picked, [k]: i })}
                                    style={{ background: 'none', border: 0, padding: 0, color: 'var(--accent3)', cursor: 'pointer', textDecoration: 'underline', font: 'inherit' }}>
                                    {t.houseTy ?? t.modelNo}
                                  </button>
                                </td>
                                <td style={S.td}>{t.exclusiveArea ?? '—'}</td>
                                <td style={S.td}>{t.suplyHshldco ?? '—'}</td>
                                <td style={S.td}>{t.spsplyHshldco ?? '—'}</td>
                                <td style={S.td}>{t.topAmount?.toLocaleString() ?? '—'}</td>
                                <td style={S.td}>
                                  {t.mktMedian != null ? `${t.mktMedian.toLocaleString()} (${t.mktBasis === 'DONG' ? '동' : '시군구'} ${t.mktCount}건)` : '—'}
                                </td>
                                <td style={{ ...S.td, color: mktLabel(t.topAmount, t.mktMedian).color }}>{mktLabel(t.topAmount, t.mktMedian).text}</td>
                                <td style={S.td}>{j ? <span style={{ ...badgeStyle(j.status), fontSize: '11px', padding: '1px 6px' }}>{statusLabel(j.status, n.kind)}</span> : '—'}</td>
                                <td style={S.td}>{j?.score?.total ?? '—'}</td>
                                <td style={S.td}>
                                  {ps?.pointShare != null ? `${Math.round(ps.pointShare * 100)}%${ps.pointShareSource === 'DOC' ? ' (공고문)' : ''}` : j?.score ? '공고 확인' : '—'}
                                </td>
                                <td style={S.td}>{rateOf(t, n.cmpet)}</td>
                                <td style={S.td}>{minScoreOf(t, n.scores)}</td>
                              </tr>
                            )
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                  {rowTypes.some((t) => t.mktMedian != null) && (
                    <div style={{ fontSize: '11px', color: 'var(--muted)', marginTop: '6px' }}>
                      시세 — 국토부 실거래 신축(5년 이내) 전용 ±5㎡ 최근 12개월 중앙값, 분양가는 최고가 기준
                    </div>
                  )}
                  {cur ? (
                    <>
                      <div style={{ ...S.mono, fontSize: '11px', color: 'var(--muted)', marginTop: '10px' }}>판정 근거{rowTypes.length > 1 ? ` — ${rowTypes[sel].houseTy ?? ''}` : ''}</div>
                      <ReasonList items={cur.reasons.filter((r) => r.decisive)} />
                      <details style={{ marginTop: '8px' }}>
                        <summary style={{ ...S.mono, fontSize: '11px', color: 'var(--muted)', cursor: 'pointer' }}>참고 {cur.reasons.filter((r) => !r.decisive).length}줄</summary>
                        <ReasonList items={cur.reasons.filter((r) => !r.decisive)} />
                      </details>
                      {cur.score && (
                        <div style={{ fontSize: '12px', color: 'var(--muted)', marginTop: '8px' }}>
                          공고일({n.rcritPblancDe}) 기준 가점 {cur.score.total}점 = 무주택 {cur.score.homeless} + 부양 {cur.score.dependents} + 통장 {cur.score.account}
                        </div>
                      )}
                    </>
                  ) : (
                    <div style={{ fontSize: '12px', color: 'var(--muted)', marginTop: '8px' }}>판정을 받는 중이다</div>
                  )}
                  {n.pblancUrl && (
                    <div style={{ textAlign: 'right', marginTop: '8px', fontSize: '12px' }}>
                      <a href={n.pblancUrl} target="_blank" rel="noopener noreferrer" style={{ color: 'var(--accent3)' }}>청약홈 공고문 ↗</a>
                    </div>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </section>

      {/* ④ 수집 이력 */}
      <section style={S.section}>
        <h2 style={S.h2}>④ 수집 이력{hoursAgo != null ? ` — 마지막 성공 ${hoursAgo}시간 전` : ''}</h2>
        {runs.error && <div className="mono" style={{ fontSize: '12px', color: 'var(--accent2, #ff6b6b)' }}>ERROR: {runs.error}</div>}
        {runs.list && (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ borderCollapse: 'collapse', width: '100%' }}>
              <thead>
                <tr>
                  {['시작', '상태', '창', '받음', '신규', '갱신', '무순위', 'API 호출', '공고문', '남음', '결과', '지오', '실거래', '시세', '오류']
                    .map((h) => <th key={h} style={S.th}>{h}</th>)}
                </tr>
              </thead>
              <tbody>
                {runs.list.map((r) => (
                  <tr key={r.id}>
                    <td style={S.td}>{r.startedAt.slice(5, 16).replace('T', ' ')}</td>
                    <td style={{ ...S.td, color: r.status === 'OK' ? 'var(--accent)' : r.status === 'FAIL' ? 'var(--accent2)' : 'var(--accent3)' }}>{r.status}</td>
                    <td style={S.td}>{r.sinceDate.slice(5)}</td>
                    <td style={S.td}>{r.fetched}</td>
                    <td style={S.td}>{r.inserted}</td>
                    <td style={S.td}>{r.updated}</td>
                    <td style={S.td}>{r.remndrCount}</td>
                    <td style={S.td}>{r.apiCalls}</td>
                    <td style={S.td}>{r.docOk}/{r.docFetched}</td>
                    <td style={S.td}>{r.docPending}</td>
                    <td style={S.td}>{r.resultOk ?? 0}/{r.resultFetched ?? 0}{r.resultPending ? ` (남음 ${r.resultPending})` : ''}</td>
                    <td style={S.td}>{r.geoOk ?? 0}/{r.geoFetched ?? 0}{r.geoPending ? ` (남음 ${r.geoPending})` : ''}</td>
                    <td style={S.td}>{r.rtmsCalls ?? 0}콜{r.rtmsPending ? ` (남음 ${r.rtmsPending})` : ''}</td>
                    <td style={S.td}>{r.mktUpdated ?? 0}</td>
                    <td style={S.td}>{r.errorMessage?.slice(0, 40) ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* ⑤ 규칙 표 */}
      <section style={S.section}>
        <details>
          <summary style={{ ...S.h2, cursor: 'pointer', margin: 0 }}>⑤ 규칙 표{rules ? ` — ${rules.version}` : ''}</summary>
          {rules && (
            <div style={{ overflowX: 'auto', marginTop: '10px' }}>
              <table style={{ borderCollapse: 'collapse', width: '100%' }}>
                <thead>
                  <tr><th style={S.th}>별표2 예치금(만원)</th>{rules.deposit.columns.map((c) => <th key={c} style={S.th}>{c}</th>)}</tr>
                </thead>
                <tbody>
                  {rules.deposit.table.map((row, i) => (
                    <tr key={i}>
                      <td style={S.td}>{i < rules.deposit.areaLimits.length ? `${rules.deposit.areaLimits[i]}㎡ 이하` : '모든 면적'}</td>
                      {row.map((v, j) => <td key={j} style={S.td}>{v.toLocaleString()}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
              <table style={{ borderCollapse: 'collapse', width: '100%', marginTop: '12px' }}>
                <thead><tr>{['1순위', '지역', '요건', '근거'].map((h) => <th key={h} style={S.th}>{h}</th>)}</tr></thead>
                <tbody>
                  {rules.rank1.map((r, i) => (
                    <tr key={i}><td style={S.td}>{r.type}</td><td style={S.td}>{r.area}</td><td style={S.td}>{r.rule}</td><td style={S.td}>{r.basis}</td></tr>
                  ))}
                </tbody>
              </table>
              <table style={{ borderCollapse: 'collapse', width: '100%', marginTop: '12px' }}>
                <thead><tr>{['가점제 비율', '규제지역', '그 밖', '근거'].map((h) => <th key={h} style={S.th}>{h}</th>)}</tr></thead>
                <tbody>
                  {rules.pointShare.map((r, i) => (
                    <tr key={i}><td style={S.td}>{r.area}</td><td style={S.td}>{r.regulated}</td><td style={S.td}>{r.other}</td><td style={S.td}>{r.basis}</td></tr>
                  ))}
                </tbody>
              </table>
              <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', marginTop: '12px' }}>
                {([['무주택기간', rules.homeless], ['부양가족', rules.dependents], ['통장 가입기간', rules.account]] as const).map(([title, rows]) => (
                  <table key={title} style={{ borderCollapse: 'collapse' }}>
                    <thead><tr><th style={S.th}>{title}</th><th style={S.th}>점수</th></tr></thead>
                    <tbody>{rows.map((r) => <tr key={r.label}><td style={S.td}>{r.label}</td><td style={S.td}>{r.points}</td></tr>)}</tbody>
                  </table>
                ))}
              </div>
              <ul style={{ fontSize: '12px', color: 'var(--muted)', paddingLeft: '18px', marginTop: '12px' }}>
                {rules.rules.map((r) => <li key={r.code}>{r.text} <span style={S.mono}>({r.basis})</span></li>)}
              </ul>
            </div>
          )}
        </details>
      </section>
    </div>
  )
}
