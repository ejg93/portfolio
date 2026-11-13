'use client'
import Sidebar from '@/components/Sidebar'
import { useEffect } from 'react'
import { flushSync } from 'react-dom'
import { useStoredFlag } from '@/lib/clientStore'

// 원이 퍼지는 속도 곡선. 빨리 나갔다가 끝에서 천천히 멈춘다
const REVEAL_EASING = 'linear(0, 0.034, 0.114, 0.215, 0.321, 0.423, 0.516, 0.599, 0.671, 0.732, 0.783, 0.825, 0.86, 0.888, 0.911, 0.93, 0.944, 0.956, 0.966, 0.973, 0.979, 0.983, 0.987, 0.99, 0.992, 0.994, 0.995, 0.996, 0.997, 0.998, 0.998, 0.999, 1)'

function applyTheme(dark: boolean) {
  const root = document.documentElement
  if (dark) root.setAttribute('data-theme', 'dark')
  else root.removeAttribute('data-theme')
}

// 테마 토글과 사이드바가 상태를 쥐고 있어 클라이언트 컴포넌트다.
// 루트 레이아웃은 서버 컴포넌트로 남겨야 metadata(탭 제목)를 내보낼 수 있다.
export default function AppShell({ children }: { children: React.ReactNode }) {
  // 마지막으로 고른 테마를 이 브라우저에만 남긴다. 저장값을 읽는 쪽은 lib/clientStore 다 —
  // 이펙트로 읽어 setState 하면 첫 렌더 뒤에 한 번 더 그리고, 쓰기를 막을 플래그도 따로 있어야 했다
  const [darkMode, setDarkMode] = useStoredFlag('theme', 'dark', 'light')

  // 팔레트는 globals.css 가 쥐고 있다. 여기서는 어느 쪽을 쓸지만 표시한다
  useEffect(() => applyTheme(darkMode), [darkMode])

  // 새 테마 화면을 버튼 중심에서 원으로 넓혀 덮는다. View Transitions 가 없거나 동작 줄이기 설정이면 바로 바꾼다
  const toggleTheme = (button: HTMLElement) => {
    const next = !darkMode
    if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setDarkMode(next)
      return
    }
    const { left, top, width, height } = button.getBoundingClientRect()
    const x = left + width / 2
    const y = top + height / 2
    const radius = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y))
    // 콜백이 끝나는 순간의 화면을 새 스냅숏으로 찍으므로 이펙트를 기다리지 않고 그 자리에서 바꾼다
    const transition = document.startViewTransition(() => {
      flushSync(() => setDarkMode(next))
      applyTheme(next)
    })
    transition.ready.then(() => {
      document.documentElement.animate(
        { clipPath: [`circle(0 at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
        { duration: 500, easing: REVEAL_EASING, pseudoElement: '::view-transition-new(root)' },
      )
    }).catch(() => {
      // 빠르게 연타하면 전환이 건너뛰어져 ready 가 거부된다. 테마는 콜백에서 이미 바뀌었다
    })
  }

  return (
    <>
      <div style={{ display: 'flex', minHeight: '100vh' }}>
        <Sidebar />
        {/* minWidth 0 이 없으면 이 칸이 본문의 min-content 아래로 안 줄어든다.
            글꼴이 넓은 환경에서는 그 폭이 화면을 넘겨 모든 라우트에 가로 스크롤이 생긴다 */}
        <div className="sidebar-push" style={{ marginLeft: 'var(--sidebar-cur)', flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          <div style={{
            position: 'fixed',
            top: 0,
            left: 'var(--sidebar-cur)',
            right: 0,
            height: '48px',
            background: 'var(--surface)',
            borderBottom: '1px solid var(--border)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            padding: '0 32px',
            zIndex: 90,
          }}>
            <button
              onClick={(e) => toggleTheme(e.currentTarget)}
              style={{
                background: 'transparent',
                border: '1px solid var(--border)',
                borderRadius: '6px',
                color: 'var(--muted)',
                cursor: 'pointer',
                fontFamily: 'var(--font-mono), monospace',
                fontSize: '11px',
                padding: '5px 14px',
                letterSpacing: '1px',
                transition: 'all 0.15s',
              }}
            >
              {darkMode ? '☀ LIGHT' : '☾ DARK'}
            </button>
          </div>
          {/* 여백은 globals.css 의 .main-body 에 있다. 폰에서 미디어쿼리로 줄인다 */}
          <main className="main-body" style={{
            flex: 1,
            minHeight: '100vh',
          }}>
            {children}
          </main>
        </div>
      </div>
    </>
  )
}
