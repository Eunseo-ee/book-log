import type { Metadata } from 'next'
import './globals.css'
import Sidebar from '@/components/sidebar/Sidebar'
import Header from '@/components/layout/Header' // 헤더 임포트

export const metadata: Metadata = {
  title: 'Archivio',
  description: '나만의 콘텐츠 기록 아카이브',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body className="bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-white antialiased">
        {/* 사이드바는 왼쪽 고정 */}
        <Sidebar />

        {/* 사이드바 너비를 제외한 나머지 영역 */}
        <div className="ml-56 flex flex-col min-h-screen">
          {/* 상단 헤더 추가 */}
          <Header />

          {/* 실제 페이지 내용 */}
          <main className="flex-1">
            <div className="max-w-4xl mx-auto px-6 py-8">
              {children}
            </div>
          </main>
        </div>
      </body>
    </html>
  )
}