'use client'
import { useState } from 'react'

import { SPRING } from '@/lib/api'

type Reply = {
  id: string
  author: string
  text: string
  likeCount: number
  publishedAt: string
}

type Comment = {
  id: string
  author: string
  text: string
  likeCount: number
  publishedAt: string
  updatedAt: string
  replyCount: number
  replies?: Reply[]
  repliesLoading?: boolean
  repliesError?: string
}

type SortKey = 'likes' | 'latest' | 'oldest' | 'length'
type SearchTarget = 'all' | 'text' | 'author'

const SEARCH_OPTIONS: { key: SearchTarget; label: string }[] = [
  { key: 'all',    label: '내용 + 작성자' },
  { key: 'text',   label: '내용' },
  { key: 'author', label: '작성자' },
]

const SORT_OPTIONS: { key: SortKey; label: string }[] = [
  { key: 'likes', label: '좋아요 많은순' },
  { key: 'latest', label: '최신순' },
  { key: 'oldest', label: '오래된순' },
  { key: 'length', label: '긴 댓글순' },
]

function extractVideoId(input: string): string | null {
  const patterns = [
    /(?:v=)([a-zA-Z0-9_-]{11})/,
    /(?:youtu\.be\/)([a-zA-Z0-9_-]{11})/,
    /(?:embed\/)([a-zA-Z0-9_-]{11})/,
    /(?:shorts\/)([a-zA-Z0-9_-]{11})/,
  ]
  for (const p of patterns) {
    const m = input.match(p)
    if (m) return m[1]
  }
  if (/^[a-zA-Z0-9_-]{11}$/.test(input.trim())) return input.trim()
  return null
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}

function formatDateTime(iso: string) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function isEdited(publishedAt: string, updatedAt: string): boolean {
  if (!updatedAt || !publishedAt) return false
  return Math.abs(new Date(updatedAt).getTime() - new Date(publishedAt).getTime()) > 5000
}

// HTML 엔티티 및 태그 파싱
function parseHtml(text: string): string {
  return text
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&nbsp;/gi, ' ')
    .replace(/<a[^>]*>(.*?)<\/a>/gi, '$1')
    .replace(/<[^>]+>/g, '')
}

function sortComments(comments: Comment[], sort: SortKey): Comment[] {
  const arr = [...comments]
  switch (sort) {
    case 'likes':  return arr.sort((a, b) => b.likeCount - a.likeCount)
    case 'latest': return arr.sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime())
    case 'oldest': return arr.sort((a, b) => new Date(a.publishedAt).getTime() - new Date(b.publishedAt).getTime())
    case 'length': return arr.sort((a, b) => b.text.length - a.text.length)
    default: return arr
  }
}

// 백엔드가 실패를 { code, message } 로 준다. code 마다 화면에 쓸 문구를 여기서 고른다.
// 할당량은 아래 전용 안내 상자로 빠지므로 코드 이름을 그대로 넘긴다
const ERROR_TEXT: Record<string, string> = {
  QUOTA_EXCEEDED: 'QUOTA_EXCEEDED',
  VIDEO_NOT_FOUND: '그 ID 로 영상을 못 찾았다. 주소나 ID 를 다시 확인한다.',
  COMMENTS_DISABLED: '그 영상은 댓글이 꺼져 있다.',
  UPSTREAM_ERROR: '유튜브 쪽 응답이 정상이 아니다. 잠시 뒤 다시 시도한다.',
}

// 옛 배포본은 맨 문자열을 주므로 JSON 이 아니면 받은 문자열을 그대로 쓴다
async function apiError(res: Response): Promise<Error> {
  const raw = await res.text()
  try {
    const body = JSON.parse(raw)
    if (body?.code) return new Error(ERROR_TEXT[body.code] ?? body.message ?? `서버 오류: ${res.status}`)
  } catch {
    // JSON 이 아니면 아래로 떨어진다
  }
  return new Error(raw || `서버 오류: ${res.status}`)
}

function isQuotaError(msg: string): boolean {
  return msg === 'QUOTA_EXCEEDED'
    || msg.toLowerCase().includes('quota')
    || msg.toLowerCase().includes('exceeded')
}

export default function YoutubePage() {
  const [url, setUrl] = useState('')
  const [comments, setComments] = useState<Comment[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [total, setTotal] = useState(0)
  const [sort, setSort] = useState<SortKey>('likes')
  const [keyword, setKeyword] = useState('')
  const [videoTitle, setVideoTitle] = useState('')
  const [minLike, setMinLike] = useState('')
  const [maxLike, setMaxLike] = useState('')
  const [searchTarget, setSearchTarget] = useState<SearchTarget>('all')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')

  async function fetchComments() {
    const videoId = extractVideoId(url)
    if (!videoId) { setError('유효한 유튜브 URL 또는 영상 ID를 입력하세요.'); return }
    setLoading(true); setError(''); setComments([]); setVideoTitle('')
    try {
      const res = await fetch(`${SPRING}/api/youtube/comments?videoId=${encodeURIComponent(videoId)}`)
      if (!res.ok) throw await apiError(res)
      const data = await res.json()
      const parsed = data.comments.map((c: Comment) => ({ ...c, text: parseHtml(c.text) }))
      setComments(parsed)
      setTotal(data.total)
      setVideoTitle(data.videoTitle || '')
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '오류가 발생했습니다.')
    } finally { setLoading(false) }
  }

  // 답글 로드 (클릭 시)
  async function loadReplies(commentId: string) {
    setComments(prev => prev.map(c =>
      c.id === commentId ? { ...c, repliesLoading: true, repliesError: undefined } : c
    ))
    try {
      const res = await fetch(`${SPRING}/api/youtube/replies?commentId=${encodeURIComponent(commentId)}`)
      if (!res.ok) throw await apiError(res)
      const data = await res.json()
      const replies: Reply[] = data.replies.map((r: Reply) => ({ ...r, text: parseHtml(r.text) }))
      setComments(prev => prev.map(c =>
        c.id === commentId ? { ...c, replies, repliesLoading: false } : c
      ))
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '오류'
      setComments(prev => prev.map(c =>
        c.id === commentId ? { ...c, repliesLoading: false, repliesError: msg } : c
      ))
    }
  }

  function toggleReplies(comment: Comment) {
    if (comment.replies !== undefined) {
      // 이미 로드됨 → 접기
      setComments(prev => prev.map(c =>
        c.id === comment.id ? { ...c, replies: undefined } : c
      ))
    } else {
      loadReplies(comment.id)
    }
  }

  const minVal = minLike !== '' ? parseInt(minLike) : 0
  const maxVal = maxLike !== '' ? parseInt(maxLike) : Infinity

  const fromTs = dateFrom ? new Date(dateFrom).getTime() : 0
  const toTs   = dateTo   ? new Date(dateTo + 'T23:59:59').getTime() : Infinity

  const filtered = comments
    .filter(c => c.likeCount >= minVal && c.likeCount <= maxVal)
    .filter(c => {
      if (!keyword) return true
      const kw = keyword.toLowerCase()
      if (searchTarget === 'text')   return c.text.toLowerCase().includes(kw)
      if (searchTarget === 'author') return c.author.toLowerCase().includes(kw)
      return c.text.toLowerCase().includes(kw) || c.author.toLowerCase().includes(kw)
    })
    .filter(c => {
      const ts = new Date(c.publishedAt).getTime()
      return ts >= fromTs && ts <= toTs
    })
  const sorted = sortComments(filtered, sort)

  const btnStyle = (active: boolean, color = 'var(--accent)'): React.CSSProperties => ({
    padding: '6px 14px',
    borderRadius: '999px',
    border: `1px solid ${active ? color : 'var(--border)'}`,
    background: active ? `${color}22` : 'transparent',
    color: active ? color : 'var(--muted)',
    cursor: 'pointer',
    fontSize: '12px',
    fontFamily: 'var(--font-mono), monospace',
    fontWeight: active ? 700 : 400,
    transition: 'all 0.15s',
  })

  const inputStyle: React.CSSProperties = {
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: '8px',
    color: 'var(--text)',
    fontSize: '13px',
    padding: '8px 12px',
    outline: 'none',
    fontFamily: 'var(--font-mono), monospace',
    width: '100px',
  }

  return (
    <div style={{ maxWidth: '860px' }}>
      {/* 헤더 */}
      <div style={{ marginBottom: '32px' }}>
        <div className="mono" style={{ fontSize: '11px', color: 'var(--yt)', letterSpacing: '4px', marginBottom: '8px' }}>
          EXPERIMENT_04
        </div>
        <h1 className="display" style={{ fontSize: '52px', lineHeight: 1, marginBottom: '8px' }}>
          <span style={{ color: 'var(--yt)' }}>YT</span>
          <span style={{ color: 'var(--text)' }}> COMMENTS</span>
        </h1>
        <p style={{ color: 'var(--muted)', fontSize: '13px', lineHeight: 1.8 }}>
          유튜브 댓글 정렬 및 검색<br />
          <span className="mono" style={{ fontSize: '11px' }}>
            ※ YouTube API 최대 1000개 사용가능 / 하루 할당량 소진 시 이용 불가
          </span>
        </p>
      </div>

      {/* URL 입력 */}
      <div style={{ display: 'flex', gap: '10px', marginBottom: '24px' }}>
        <input
          name="url" value={url}
          onChange={e => setUrl(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && fetchComments()}
          placeholder="유튜브 URL 또는 영상 ID 입력..."
          style={{
            // flex 아이템의 min-width 기본값은 auto 라 입력칸이 제 내용 폭 아래로 안 줄어든다.
            // 글꼴이 넓은 환경에서는 그 폭이 화면을 넘겨 가로 스크롤이 생긴다
            flex: 1, minWidth: 0, padding: '12px 16px',
            background: 'var(--surface)', border: '1px solid var(--border)',
            borderRadius: '8px', color: 'var(--text)', fontSize: '14px', outline: 'none',
            fontFamily: 'var(--font-sans), sans-serif',
          }}
        />
        <button onClick={fetchComments} disabled={loading} style={{
          padding: '12px 24px',
          background: loading ? 'var(--border)' : 'var(--yt-btn)',
          color: '#fff', border: 'none', borderRadius: '8px',
          cursor: loading ? 'not-allowed' : 'pointer',
          fontWeight: 700, fontSize: '14px',
          fontFamily: 'var(--font-mono), monospace',
          transition: 'all 0.15s',
        }}>
          {loading ? '로딩 중...' : '▶ 불러오기'}
        </button>
      </div>

      {/* 오류 */}
      {error && (
        <div style={{
          padding: '14px 18px', borderRadius: '8px', marginBottom: '20px',
          background: 'rgba(255,68,68,0.1)', border: '1px solid rgba(255,68,68,0.3)',
          color: '#ff6666', fontSize: '13px', lineHeight: 1.7,
        }}>
          {isQuotaError(error) ? (
            <>
              <div style={{ fontWeight: 700, marginBottom: '6px' }}>⚠ YouTube API 할당량 초과</div>
              <div>오늘 사용 가능한 API 호출 횟수를 모두 소진했어요.</div>
              <div className="mono" style={{ fontSize: '11px', marginTop: '6px', color: 'var(--muted)' }}>
                태평양 시간 자정(한국 오후 4시)에 초기화됩니다.
              </div>
            </>
          ) : error}
        </div>
      )}

      {/* 결과 */}
      {comments.length > 0 && (
        <>
          <div style={{ marginBottom: '16px' }}>
            {videoTitle && (
              <div style={{ fontSize: '14px', color: 'var(--text)', fontWeight: 600, marginBottom: '6px' }}>
                {videoTitle}
              </div>
            )}
            <div className="mono" style={{ fontSize: '12px', color: 'var(--muted)' }}>
              전체 {total.toLocaleString()}개 중 {comments.length.toLocaleString()}개 수집
              → 필터 후 <span style={{ color: 'var(--accent)' }}>{sorted.length.toLocaleString()}개</span> 표시
            </div>
          </div>

          {/* 정렬 */}
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '12px' }}>
            {SORT_OPTIONS.map(s => (
              <button key={s.key} onClick={() => setSort(s.key)} style={btnStyle(sort === s.key)}>
                {s.label}
              </button>
            ))}
          </div>

          {/* 좋아요 범위 필터 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px', flexWrap: 'wrap' }}>
            <span className="mono" style={{ fontSize: '11px', color: 'var(--muted)' }}>좋아요</span>
            <input
              type="number"
              name="minLike" value={minLike}
              onChange={e => setMinLike(e.target.value)}
              placeholder="최소"
              min={0}
              style={inputStyle}
            />
            <span className="mono" style={{ fontSize: '11px', color: 'var(--muted)' }}>~</span>
            <input
              type="number"
              name="maxLike" value={maxLike}
              onChange={e => setMaxLike(e.target.value)}
              placeholder="최대"
              min={0}
              style={inputStyle}
            />
            <span className="mono" style={{ fontSize: '11px', color: 'var(--muted)' }}>개</span>
            {(minLike || maxLike) && (
              <button onClick={() => { setMinLike(''); setMaxLike('') }} style={{
                ...btnStyle(false),
                fontSize: '11px', padding: '4px 10px',
              }}>초기화</button>
            )}
          </div>

          {/* 키워드 검색 */}
          <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
            <select
              name="searchTarget" value={searchTarget}
              onChange={e => setSearchTarget(e.target.value as SearchTarget)}
              style={{
                padding: '10px 12px',
                background: 'var(--surface)', border: '1px solid var(--border)',
                borderRadius: '8px', color: 'var(--text)', fontSize: '13px', outline: 'none',
                fontFamily: 'var(--font-mono), monospace', cursor: 'pointer',
                flexShrink: 0,
              }}
            >
              {SEARCH_OPTIONS.map(o => (
                <option key={o.key} value={o.key}>{o.label}</option>
              ))}
            </select>
            <input
              name="keyword" value={keyword}
              onChange={e => setKeyword(e.target.value)}
              placeholder="검색어 입력..."
              style={{
                flex: 1, padding: '10px 14px',
                background: 'var(--surface)', border: '1px solid var(--border)',
                borderRadius: '8px', color: 'var(--text)', fontSize: '13px', outline: 'none',
                fontFamily: 'var(--font-sans), sans-serif',
              }}
            />
          </div>

          {/* 날짜 범위 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px', flexWrap: 'wrap' }}>
            <span className="mono" style={{ fontSize: '11px', color: 'var(--muted)' }}>작성일</span>
            <input
              type="date"
              name="dateFrom" value={dateFrom}
              onChange={e => setDateFrom(e.target.value)}
              style={{
                padding: '8px 12px',
                background: 'var(--surface)', border: '1px solid var(--border)',
                borderRadius: '8px', color: 'var(--text)', fontSize: '13px', outline: 'none',
                fontFamily: 'var(--font-mono), monospace',
                colorScheme: 'dark',
              }}
            />
            <span className="mono" style={{ fontSize: '11px', color: 'var(--muted)' }}>~</span>
            <input
              type="date"
              name="dateTo" value={dateTo}
              onChange={e => setDateTo(e.target.value)}
              style={{
                padding: '8px 12px',
                background: 'var(--surface)', border: '1px solid var(--border)',
                borderRadius: '8px', color: 'var(--text)', fontSize: '13px', outline: 'none',
                fontFamily: 'var(--font-mono), monospace',
                colorScheme: 'dark',
              }}
            />
            {(dateFrom || dateTo) && (
              <button onClick={() => { setDateFrom(''); setDateTo('') }} style={{
                ...btnStyle(false), fontSize: '11px', padding: '4px 10px',
              }}>초기화</button>
            )}
          </div>

          {/* 댓글 목록 */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {sorted.map((c, i) => (
              <div key={c.id} style={{
                background: 'var(--surface)',
                border: '1px solid var(--border)',
                borderRadius: '10px',
                padding: '16px 18px',
                transition: 'border-color 0.15s',
              }}
                onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--accent)')}
                onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}
              >
                {/* 상단 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <div className="mono" style={{ fontSize: '10px', color: 'var(--muted)' }}>
                      #{String(i + 1).padStart(3, '0')}
                    </div>
                    <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text)' }}>{c.author}</div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span style={{
                      fontSize: '12px', color: 'var(--yt)',
                      fontFamily: 'var(--font-mono), monospace', fontWeight: 700,
                    }}>
                      ♥ {c.likeCount.toLocaleString()}
                    </span>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '2px' }}>
                      <span className="mono" style={{ fontSize: '11px', color: 'var(--muted)' }}>
                        {formatDateTime(c.publishedAt)}
                      </span>
                      {isEdited(c.publishedAt, c.updatedAt) && (
                        <span className="mono" style={{ fontSize: '10px', color: 'var(--accent3)' }}>
                          수정: {formatDateTime(c.updatedAt)}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {/* 본문 */}
                <p style={{
                  fontSize: '14px', color: 'var(--muted)',
                  lineHeight: 1.8, margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                }}>
                  {keyword
                    ? c.text.split(new RegExp(`(${keyword})`, 'gi')).map((part, j) =>
                        part.toLowerCase() === keyword.toLowerCase()
                          ? <mark key={j} style={{ background: 'rgba(255,212,0,0.3)', color: 'var(--text)', borderRadius: '2px' }}>{part}</mark>
                          : part
                      )
                    : c.text
                  }
                </p>

                {/* 답글 버튼 */}
                {c.replyCount > 0 && (
                  <div style={{ marginTop: '12px' }}>
                    <button
                      onClick={() => toggleReplies(c)}
                      disabled={c.repliesLoading}
                      style={{
                        background: 'transparent',
                        border: '1px solid var(--border)',
                        borderRadius: '999px',
                        color: 'var(--accent3)',
                        cursor: c.repliesLoading ? 'wait' : 'pointer',
                        fontSize: '11px',
                        padding: '4px 12px',
                        fontFamily: 'var(--font-mono), monospace',
                        transition: 'all 0.15s',
                      }}
                    >
                      {c.repliesLoading
                        ? '로딩 중...'
                        : c.replies !== undefined
                          ? '▲ 답글 접기'
                          : `▼ 답글 ${c.replyCount}개 보기`
                      }
                    </button>

                    {/* 답글 오류 */}
                    {c.repliesError && (
                      <div style={{ marginTop: '8px', fontSize: '12px', color: '#ff6666' }}>
                        {isQuotaError(c.repliesError)
                          ? '⚠ API 할당량 초과로 답글을 불러올 수 없어요.'
                          : `오류: ${c.repliesError}`
                        }
                      </div>
                    )}

                    {/* 답글 목록 */}
                    {c.replies && c.replies.length > 0 && (
                      <div style={{ marginTop: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        {c.replies.map(r => (
                          <div key={r.id} style={{
                            marginLeft: '16px',
                            padding: '12px 14px',
                            background: 'var(--surface2)',
                            borderLeft: '2px solid var(--accent3)',
                            borderRadius: '0 8px 8px 0',
                          }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                              <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text)' }}>{r.author}</span>
                              <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                                {r.likeCount > 0 && (
                                  <span style={{ fontSize: '11px', color: 'var(--yt)', fontFamily: 'var(--font-mono), monospace' }}>
                                    ♥ {r.likeCount.toLocaleString()}
                                  </span>
                                )}
                                <span className="mono" style={{ fontSize: '10px', color: 'var(--muted)' }}>
                                  {formatDate(r.publishedAt)}
                                </span>
                              </div>
                            </div>
                            <p style={{ fontSize: '13px', color: 'var(--muted)', lineHeight: 1.7, margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                              {r.text}
                            </p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>

          {sorted.length === 0 && (
            <div style={{ textAlign: 'center', padding: '40px', color: 'var(--muted)' }}>
              조건에 맞는 댓글이 없어요.
            </div>
          )}
        </>
      )}
    </div>
  )
}
