import type { MetadataRoute } from 'next'

// 색인 대상 라우트 목록. /about 은 robots noindex 라 뺐고(app/about/layout.tsx),
// app/_ai-battle 는 밑줄로 시작해 라우팅에서 제외되는 폴더라 주소가 없다.
const SITE = process.env.NEXT_PUBLIC_SITE_URL ?? 'https://public-alpha-roan.vercel.app'

const ROUTES = ['', '/projectshop', '/workflow', '/toolbox', '/study', '/board', '/youtube', '/public-data', '/cheongyak'] as const

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date()
  return ROUTES.map((path) => ({
    url: `${SITE}${path}`,
    lastModified: now,
    changeFrequency: 'monthly' as const,
    priority: path === '' ? 1 : 0.7,
  }))
}
