/** @type {import('next').NextConfig} */
const nextConfig = {
  // 백엔드 API 프록시 — CORS 없이 /api/* 를 Spring으로 포워딩
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'}/api/:path*`,
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

module.exports = nextConfig
