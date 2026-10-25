import { test, expect, type ConsoleMessage } from '@playwright/test'
import { BACKEND_HOST } from './routes'

// 백엔드가 떠 있을 때만 도는 검사. 다른 스펙은 8080 연결 실패를 무시하도록 해 뒀으니
// 프론트에서 스프링을 거쳐 외부 API 까지 가는 길은 여기서만 본다.
// CI 에는 백엔드가 없어서 통째로 건너뛴다.
// 생사 판정용 문은 따로 없다. 사람인 키가 없어도 200 에 빈 목록을 주는 /api/jobs 로 본다
const ALIVE = `http://${BACKEND_HOST}/api/jobs`

// 이 파일만 진짜 외부 API 를 부른다. 유튜브 쪽 지연·할당량은 우리가 못 고르는 값이라
// 여기서만 재시도를 켠다. 다른 스펙은 재시도 없이 한 번에 판정한다.
test.describe.configure({ retries: 2 })

let backendUp = false

test.beforeAll(async ({ request }) => {
  try {
    const res = await request.get(ALIVE, { timeout: 3000 })
    backendUp = res.ok()
  } catch {
    backendUp = false
  }
})

test.beforeEach(() => {
  // 배포본을 겨눌 때는 그쪽이 부르는 백엔드가 따로 있다. 로컬 8080 을 섞어 보지 않는다
  test.skip(!!process.env.E2E_BASE_URL, '배포본 대상 실행이라 로컬 백엔드 검사는 건너뛴다')
  test.skip(!backendUp, `백엔드가 ${BACKEND_HOST} 에 없다`)
})

test('/public-data 가 백엔드 응답으로 화면을 그린다', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (m: ConsoleMessage) => m.type() === 'error' && errors.push(m.text()))
  page.on('pageerror', (e) => errors.push(e.message))

  const jobsCall = page.waitForResponse((r) => r.url().includes('/api/jobs'))
  await page.goto('/public-data', { waitUntil: 'domcontentloaded' })
  const res = await jobsCall

  expect(res.status(), '/api/jobs 응답 코드').toBe(200)
  const body = await res.json()
  expect(body, 'jobs 배열이 없다').toHaveProperty('jobs')

  // 사람인 키가 비면 error 필드가 담긴 빈 목록이 온다. 화면이 그 상태에서도 안 깨져야 한다
  await expect(page.locator('h1')).toBeVisible()
  expect(errors, '콘솔 에러').toEqual([])
})

test('/youtube 는 잘못된 영상 ID 에 오류 상자를 띄운다', async ({ page }) => {
  // 프론트 → 스프링 → YouTube API 까지 실제로 갔다 오는 길을 확인한다.
  // 성공 응답은 영상이 지워지거나 댓글이 막히면 흔들려서, 어느 쪽이든 같은 답이 오는 실패 쪽을 본다.
  // ID 는 11자 규칙을 통과해야 한다. 아니면 프론트가 먼저 막아서 요청 자체가 안 나간다
  await page.goto('/youtube', { waitUntil: 'domcontentloaded' })

  const call = page.waitForResponse((r) => r.url().includes('/api/youtube/comments'))
  await page.getByPlaceholder('유튜브 URL 또는 영상 ID 입력...').fill('zzzzzzzzzzz')
  await page.getByRole('button', { name: '불러오기' }).click()

  const res = await call
  // 없는 영상이면 404, 그날 할당량을 다 썼으면 429 다. 둘 다 프론트→스프링→유튜브 왕복이 끝까지 갔다는 뜻이고,
  // 어느 쪽이 오는지는 우리가 못 고른다
  const body = await res.json()
  expect([404, 429], `받은 상태 ${res.status()} · 코드 ${body.code}`).toContain(res.status())
  expect(['VIDEO_NOT_FOUND', 'QUOTA_EXCEEDED']).toContain(body.code)

  // 버튼이 로딩 상태에 갇히지 않고 오류 문구가 화면에 나와야 한다
  await expect(page.getByRole('button', { name: '불러오기' })).toBeEnabled({ timeout: 15000 })
  await expect(page.locator('text=/영상을 못 찾았다|할당량/').first()).toBeVisible()
})

test('/cheongyak 이 수집 이력·공고를 백엔드에서 받아 그린다', async ({ page, request }) => {
  // 프론트 → 스프링 → Postgres 길. 공고가 비어 있어도(수집 전) 화면은 안 깨져야 한다
  const errors: string[] = []
  page.on('console', (m: ConsoleMessage) => m.type() === 'error' && errors.push(m.text()))
  page.on('pageerror', (e) => errors.push(e.message))

  const runsCall = page.waitForResponse((r) => r.url().includes('/api/cheongyak/runs'))
  await page.goto('/cheongyak', { waitUntil: 'domcontentloaded' })
  expect((await runsCall).status(), '/api/cheongyak/runs 응답 코드').toBe(200)
  await expect(page.locator('h1')).toBeVisible()

  // 규칙 표는 엔진과 같은 상수에서 나온다 — 예치금표·경기 시군 목록이 있어야 한다
  const rules = await (await request.get(`http://${BACKEND_HOST}/api/cheongyak/rules`)).json()
  expect(rules, '규칙 표에 예치금이 없다').toHaveProperty('deposit')
  expect(rules.ggCities, '경기 시군 목록').toContain('평택')
  expect(errors, '콘솔 에러').toEqual([])
})
