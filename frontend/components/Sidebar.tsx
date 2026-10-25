'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useState } from 'react'
import { useHydrated, useMediaQuery, useStoredFlag } from '@/lib/clientStore'

const menus = [
  { label: 'HOME',         href: '/',            icon: '⌂', desc: '소개' },
  // /about 은 라우트만 남긴다. Q&A 형식이 리뷰어 동선에 안 맞아 메뉴에서 뺐다
  { label: 'PROJECT SHOP', href: '/projectshop', icon: '▤', desc: '설계 기록' },
  { label: 'AI WORKFLOW',  href: '/workflow',    icon: '⚙', desc: '검증 체계' },
  { label: 'TOOLBOX',      href: '/toolbox',     icon: '🧰', desc: '폐쇄망 도구' },
  { label: 'STUDY NOTES',  href: '/study',       icon: '✎', desc: '학습 노트' },
]

// 리뷰어 동선에서 빠지는 것. 아래 LAB 묶음으로 내린다
const labMenus = [
  { label: 'CHEONGYAK',    href: '/cheongyak',   icon: '⌂', desc: '공공데이터 배치' },
  { label: 'DEV LOG',      href: '/board',       icon: '✍', desc: '기록' },
  { label: 'YT COMMENTS',  href: '/youtube',     icon: '▶', desc: '실험' },
]

// 이 폭 이하는 폰으로 본다. globals.css 의 미디어쿼리와 같은 값
const MOBILE_QUERY = '(max-width: 767px)'

export default function Sidebar() {
  const path = usePathname()
  // 마지막 접힘 상태를 이 브라우저에만 남긴다. 개인정보가 아니고 서버로도 안 간다
  const [collapsed, setCollapsed] = useStoredFlag('sidebar-collapsed', '1', '0')
  // 수화 전에는 전환 효과를 끈다. 접힌 채로 들어왔는데 220px 에서 밀려오면 눈에 띈다
  const restored = useHydrated()
  // 폰에서는 레일 대신 서랍이다. 본문을 밀지 않고 위에 덮인다
  const mobile = useMediaQuery(MOBILE_QUERY)
  const [drawerOpen, setDrawerOpen] = useState(false)

  // 페이지를 옮기면 서랍을 닫는다. 이펙트가 아니라 렌더 중에 고친다 —
  // 이펙트로 닫으면 서랍이 열린 채로 한 번 그려진 뒤 닫힌다
  const [pathAtOpen, setPathAtOpen] = useState(path)
  if (pathAtOpen !== path) {
    setPathAtOpen(path)
    setDrawerOpen(false)
  }

  // 서랍 열림을 html 속성으로도 알린다. 폰에서 서랍을 숨기는 쪽은 globals.css 의 미디어쿼리라
  // (서버가 그린 첫 화면부터 밖에 있어야 한다) 열 때도 CSS 가 그 속성을 보고 들여온다
  useEffect(() => {
    if (mobile && drawerOpen) document.documentElement.setAttribute('data-drawer', 'open')
    else document.documentElement.removeAttribute('data-drawer')
  }, [mobile, drawerOpen])

  // 본문 여백과 상단 바 위치가 같은 값을 봐야 접을 때 빈 띠가 안 남는다.
  // 폰에서는 서랍이 본문 위에 뜨므로 여백을 0 으로 둔다
  useEffect(() => {
    const cur = mobile ? '0px' : collapsed ? '60px' : '220px'
    document.documentElement.style.setProperty('--sidebar-cur', cur)
    if (!restored) return
    document.documentElement.setAttribute('data-sidebar-ready', '1')
  }, [collapsed, restored, mobile])

  // 폰에서는 항상 펼친 모양으로 그린다
  const narrow = !mobile && collapsed

  const renderMenu = (m: typeof menus[number]) => {
    const active = path === m.href || (m.href !== '/' && path.startsWith(m.href))
    return (
      <Link key={m.href} href={m.href} title={narrow ? m.label : undefined} style={{
        display: 'flex', alignItems: 'center',
        justifyContent: narrow ? 'center' : 'flex-start',
        gap: '10px', padding: narrow ? '10px 0' : '10px 14px',
        borderRadius: '6px', textDecoration: 'none',
        background: active ? 'rgba(0,255,136,0.08)' : 'transparent',
        border: active ? '1px solid rgba(0,255,136,0.2)' : '1px solid transparent',
        transition: 'all 0.15s',
      }}>
        <span style={{ fontSize: '18px', flexShrink: 0 }}>{m.icon}</span>
        {!narrow && (
          <div>
            <div className="mono" style={{ fontSize: '10px', color: 'var(--muted)', letterSpacing: '1px' }}>{m.desc}</div>
            <div style={{ fontSize: '13px', fontWeight: 700, color: active ? 'var(--accent)' : 'var(--text)' }}>{m.label}</div>
          </div>
        )}
      </Link>
    )
  }

  return (
    <>
      {/* 폰 전용 열기 버튼. 상단 바(AppShell) 왼쪽 자리에 겹쳐 놓는다 */}
      {mobile && (
        <button
          type="button"
          onClick={() => setDrawerOpen(true)}
          aria-label="메뉴 열기"
          aria-expanded={drawerOpen}
          style={{
            position: 'fixed', top: '9px', left: '12px', zIndex: 95,
            background: 'transparent', border: '1px solid var(--border)',
            borderRadius: '6px', color: 'var(--text)', cursor: 'pointer',
            fontSize: '16px', padding: '4px 10px', lineHeight: 1.4,
          }}
        >
          ☰
        </button>
      )}
      {mobile && drawerOpen && (
        <div className="drawer-backdrop" onClick={() => setDrawerOpen(false)} />
      )}

      <aside className="sidebar-rail" style={{
        width: mobile ? '240px' : 'var(--sidebar-cur)',
        background: 'var(--surface)',
        borderRight: '1px solid var(--border)',
        position: 'fixed',
        top: 0, left: 0, bottom: 0,
        display: 'flex',
        flexDirection: 'column',
        padding: narrow ? '32px 10px' : '32px 20px',
        zIndex: 100,
        overflowY: 'auto',
        overflowX: 'hidden',
        // 폰에서는 화면 밖에 뒀다가 열 때만 들어온다
        transform: mobile && !drawerOpen ? 'translateX(-100%)' : 'none',
        transition: mobile ? 'transform 0.2s ease' : undefined,
      }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: narrow ? 'center' : 'space-between', marginBottom: '28px', flexShrink: 0 }}>
          {!narrow && (
            <div>
              <div className="display" style={{ fontSize: '28px', color: 'var(--text)', lineHeight: 1 }}>DEV</div>
              <div className="display" style={{ fontSize: '28px', color: 'var(--accent)', lineHeight: 1 }}>PORTFOLIO</div>
              <div className="mono" style={{ fontSize: '10px', color: 'var(--muted)', marginTop: '8px', letterSpacing: '1px' }}>JAVA · SPRING · NEXT.JS</div>
            </div>
          )}
          <button
            type="button"
            onClick={() => (mobile ? setDrawerOpen(false) : setCollapsed(!collapsed))}
            aria-label={mobile ? '메뉴 닫기' : collapsed ? '메뉴 펼치기' : '메뉴 접기'}
            style={{
              background: 'transparent', border: '1px solid var(--border)',
              borderRadius: '6px', color: 'var(--muted)', cursor: 'pointer',
              fontSize: '14px', padding: '6px 8px', lineHeight: 1, flexShrink: 0,
            }}
          >
            {mobile ? '✕' : collapsed ? '▶' : '◀'}
          </button>
        </div>

        {!narrow && <div style={{ height: '1px', background: 'var(--border)', marginBottom: '20px', flexShrink: 0 }} />}

        <nav style={{ display: 'flex', flexDirection: 'column', gap: '4px', flexShrink: 0 }}>
          {menus.map(renderMenu)}
          {!narrow && (
            <div className="mono" style={{ fontSize: '9px', color: 'var(--muted)', letterSpacing: '3px', margin: '14px 14px 2px' }}>
              LAB
            </div>
          )}
          {narrow && <div style={{ height: '1px', background: 'var(--border)', margin: '10px 4px' }} />}
          {labMenus.map(renderMenu)}
        </nav>

        {!narrow && (
          <div style={{ marginTop: 'auto', paddingTop: '16px', flexShrink: 0 }}>
            <div style={{ height: '1px', background: 'var(--border)', marginBottom: '16px' }} />
            <div className="mono" style={{ fontSize: '10px', color: 'var(--muted)', lineHeight: 2 }}>
              <div>EXP: 5 YRS</div>
              <div>STACK: JSP/JAVA/SPRING</div>
              <div style={{ color: 'var(--accent3)' }}>+ NEXT.JS</div>
            </div>
          </div>
        )}
      </aside>
    </>
  )
}
