// 외부 API 엔드포인트 / 키 중앙 관리

// ── Spring Boot 백엔드 ────────────────────────────────
export const SPRING = process.env.NEXT_PUBLIC_SPRING_URL || 'http://localhost:8080'
export const CHEONGYAK = `${SPRING}/api/cheongyak`

// ── Kakao ─────────────────────────────────────────────
// JS SDK (브라우저): NEXT_PUBLIC_KAKAO_JS_KEY (.env.local)
// REST API (서버사이드): KAKAO_REST_KEY (.env.local)
export const KAKAO_JS_KEY = process.env.NEXT_PUBLIC_KAKAO_JS_KEY || ''

// ── Saramin / Kakao ───────────────────────────────────
// 키는 백엔드 application.properties 에서 관리
// saramin.access.key / kakao.rest.key
