const { PHASE_DEVELOPMENT_SERVER, PHASE_PRODUCTION_BUILD } = require('next/constants')

// 개발 서버에서만 쓰는 기본값. production 빌드에서는 쓰지 않는다
const DEV_API_URL = 'http://localhost:8080'

// NEXT_PUBLIC_API_URL을 읽는 유일한 곳(검증·정규화). 결과는 아래 env로 번들에 박히고 api.ts와 rewrites가 같은 값을 쓴다.
// NEXT_PUBLIC_* 는 빌드 시점에 번들에 인라인되므로 값은 URL뿐이어야 하고 시크릿을 넣으면 안 된다(브라우저에 그대로 노출).
function resolveApiUrl(phase) {
  const url = (process.env.NEXT_PUBLIC_API_URL ?? '').trim().replace(/\/+$/, '')
  if (url) return url

  if (phase === PHASE_DEVELOPMENT_SERVER) return DEV_API_URL
  if (phase === PHASE_PRODUCTION_BUILD) {
    throw new Error('NEXT_PUBLIC_API_URL이 비어 있습니다. production 빌드에는 백엔드 주소가 필요합니다 (빌드 시점에 번들에 고정됨).')
  }
  // next start: 번들은 이미 빌드되어 값이 고정돼 있다. 런타임 환경변수로는 바꿀 수 없다는 경고만 남긴다
  console.warn('[book-log] NEXT_PUBLIC_API_URL이 없습니다. 빌드 때 주입한 값이 쓰이며 런타임에는 바꿀 수 없습니다.')
  return ''
}

module.exports = (phase) => {
  const apiUrl = resolveApiUrl(phase)

  /** @type {import('next').NextConfig} */
  return {
    env: { NEXT_PUBLIC_API_URL: apiUrl },
    // 백엔드 API 프록시 — /api/* 를 Spring으로 포워딩 (서버끼리 호출이라 CORS와 무관)
    async rewrites() {
      return [
        {
          source: '/api/:path*',
          destination: `${apiUrl}/api/:path*`,
        },
      ]
    },
    images: {
      remotePatterns: [
        { protocol: 'https', hostname: '**.kakao.com' },
        { protocol: 'https', hostname: '**.themoviedb.org' },
        { protocol: 'https', hostname: '**.anilist.co' },
      ],
    },
  }
}
