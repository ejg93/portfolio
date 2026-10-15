import { test, expect, type ConsoleMessage } from '@playwright/test'
import { isDeployNoise, isGenericResourceError } from './routes'

// public/ 아래 정적 HTML 은 앱 코드가 아니라서 typecheck·lint·build 가 한 줄도 안 본다.
// 그래서 여기서만 잡히는 것이 있다 — 2026-09-18 에 selfstudy 노트가 없는 엘리먼트를 잡으려다
// 매 이동마다 예외를 던지고 있었고, 화면은 멀쩡해 보였다.
const PAGES = [
  '/study/ai-native-notes',
  '/study/architecture-fundamentals',
  '/study/architecture-notes',
  '/study/nextjs-notes',
  '/study/projectshop-notes',
  '/study/selfstudy-plan-notes',
  '/docrules/hwp-excel-rules',
  '/game/limbus/simulator',
  '/game/limbus/ego-gifts',
  '/jobhunt/company-filter.html',
]

for (const path of PAGES) {
  test(`${path} 가 조용히 열린다`, async ({ page }) => {
    const errors: string[] = []
    const failed: string[] = []

    page.on('console', (m: ConsoleMessage) => {
      if (m.type() !== 'error') return
      const text = m.text()
      if (isDeployNoise(text) || isGenericResourceError(text)) return
      errors.push(text.slice(0, 160))
    })
    page.on('pageerror', (e) => errors.push('예외: ' + e.message.slice(0, 160)))
    page.on('response', (r) => {
      if (r.status() >= 400 && !isDeployNoise(r.url())) failed.push(`${r.status()} ${r.url().slice(0, 80)}`)
    })

    const res = await page.goto(path, { waitUntil: 'load' })
    expect(res?.status(), `${path} 응답 코드`).toBe(200)
    await page.waitForTimeout(300)

    const bodyLen = await page.evaluate(() => document.body.innerText.trim().length)
    expect(bodyLen, `${path} 본문이 비었다`).toBeGreaterThan(200)

    expect(errors, `${path} 콘솔·예외`).toEqual([])
    expect(failed, `${path} 실패한 요청`).toEqual([])
  })
}

test('학습 노트의 이전·다음 버튼이 살아 있다', async ({ page }) => {
  // 노트 다섯 장은 같은 뼈대를 쓴다. pager 엘리먼트가 빠지면 go() 가 거기서 터져
  // 해시 동기화·스크롤·hashchange 등록이 통째로 안 돈다
  const notes = PAGES.filter((p) => p.startsWith('/study/'))
  for (const path of notes) {
    await page.goto(path, { waitUntil: 'load' })
    const buttons = await page.locator('#pager button').count()
    expect(buttons, `${path} 의 이전·다음 버튼`).toBe(2)
  }
})
