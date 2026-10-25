// 백엔드 /api/cheongyak 응답 모양과 화면 상태

export type Doc = {
  status: string | null
  remndrArea: string | null
  remndrHomeless: boolean | null
  rewinYears: number | null
  regulation: string | null
  crspCity: string | null
  crspMonths: number | null
  resPriArea: string | null
  resPriMonths: number | null
  pointLe60: number | null
  pointLe85: number | null
  pointGt85: number | null
}

export type HouseTypeView = {
  modelNo: string
  houseTy: string | null
  exclusiveArea: number | null
  supplyArea: number | null
  suplyHshldco: number | null
  spsplyHshldco: number | null
  topAmount: number | null
}

export type NoticeView = {
  houseManageNo: string
  pblancNo: string
  kind: string
  kindLabel: string
  houseSecd: string | null
  houseDtlSecd: string | null
  houseNm: string
  areaNm: string | null
  address: string | null
  totSuplyHshldco: number | null
  rcritPblancDe: string | null
  rceptBgnde: string | null
  rceptEndde: string | null
  rnk1CrspBgnde: string | null
  regulated: boolean
  pubHousing: boolean
  specltRdnAt: string | null
  mdatTrgetAt: string | null
  pblancUrl: string | null
  doc: Doc
  houseTypes: HouseTypeView[]
}

export type Reason = {
  level: 'PASS' | 'FAIL' | 'CHECK' | 'INFO'
  scope: string
  decisive: boolean
  code: string
  text: string
  basis: string
}

export type Status = 'RANK1' | 'RANK2' | 'INELIGIBLE' | 'NOT_APPLICABLE'

export type Judgement = {
  houseManageNo: string
  pblancNo: string
  modelNo: string | null
  status: Status
  hasCheck: boolean
  reasons: Reason[]
  score: { total: number; homeless: number; dependents: number; account: number; spouseBonus: number; homelessYears: number } | null
  lottery: { pointShare: number | null; pointShareSource: 'LAW' | 'DOC' | 'NONE'; note: string | null } | null
  receipt: { label: string; rank1Begin: string | null; rank1End: string | null }
}

export type CollectRun = {
  id: number
  startedAt: string
  finishedAt: string | null
  status: 'RUNNING' | 'OK' | 'FAIL'
  triggerSrc: string
  sinceDate: string
  fetched: number
  inserted: number
  updated: number
  remndrCount: number
  apiCalls: number
  docFetched: number
  docOk: number
  docPending: number
  errorMessage: string | null
}

export type Rules = {
  version: string
  homeless: { label: string; points: number }[]
  dependents: { label: string; points: number }[]
  account: { label: string; points: number }[]
  deposit: { areaLimits: number[]; columns: string[]; table: number[][] }
  rank1: { type: string; area: string; rule: string; basis: string }[]
  pointShare: { area: string; regulated: string; other: string; basis: string }[]
  sidos: string[]
  ggCities: string[]
  rules: { code: string; text: string; basis: string }[]
}

/** 조건 칸. 날짜는 'YYYY-MM-DD', 비면 '' */
export type Cond = {
  sido: string
  birth: string
  marriage: string
  houses: number
  homelessSince: string
  deps: number
  acct: number
  pay: number
  deposit: number
  spAcct: number
  resMonths: number
  city: string
  cityMonths: number
  householder: boolean
  won5: boolean
  won2: boolean
}

export type Filters = {
  area: 'capital' | '서울' | '경기' | '인천' | 'all'
  kind: 'all' | 'APT01' | 'APT03' | 'REMNDR' | 'etc'
  period: 90 | 180 | 365
  rcpt: 'live' | 'all'
  size: 'all' | 's' | 'm' | 'l'
  result: 'all' | 'ok' | 'r1'
}

export type Slot = { name: string; cond: Cond; filters: Filters; savedAt: string }
