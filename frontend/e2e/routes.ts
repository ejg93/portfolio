// 검사 대상 라우트. app/_ai-battle 은 밑줄 폴더라 주소가 없어서 뺐다.
// /about 은 색인에서만 빠졌을 뿐 주소는 살아 있어서 넣는다.
export const ROUTES = [
  '/',
  '/projectshop',
  '/workflow',
  '/toolbox',
  '/study',
  '/board',
  '/youtube',
  '/public-data',
  '/cheongyak',
  '/about',
] as const

// 백엔드가 떠 있어야 데이터가 오는 화면. 안 떠 있을 때 나는 연결 실패는 화면 결함이 아니라 무시한다
export const BACKEND_HOST = 'localhost:8080'

export const isBackendNoise = (text: string) =>
  text.includes(BACKEND_HOST) ||
  text.includes('ERR_CONNECTION_REFUSED') ||
  text.includes('Failed to fetch')

// 배포본을 겨눌 때만 나오는 잡음. Vercel 이 프리뷰에 끼워 넣는 툴바가 제 인증을 받으려다 403 을 내고
// 로그인 상태가 아니면 계정 목록이 비었다고 찍는다. 우리 화면 코드와 무관하다
export const isDeployNoise = (text: string) =>
  text.includes('vercel.com/api/jwt') ||
  text.includes('vercel.live') ||
  text.includes("Provider's accounts list is empty")

// 「Failed to load resource: ...」 는 어느 자원인지 안 적혀 온다. 같은 실패를 응답 리스너가
// URL 까지 달고 따로 보고 있으므로, 정보가 없는 이 줄은 중복으로 친다
export const isGenericResourceError = (text: string) => text.startsWith('Failed to load resource')
