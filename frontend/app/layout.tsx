import type { Metadata } from 'next'
import './globals.css'
import Sidebar from '@/components/sidebar/Sidebar'

export const metadata: Metadata = {
  title: 'Archivio',
  description: '나만의 콘텐츠 기록 아카이브',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body className="bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-white antialiased">
        <Sidebar />
        {/* 사이드바 너비(w-56 = 224px) 만큼 오프셋 */}
        <main className="ml-56 min-h-screen">
          <div className="max-w-4xl mx-auto px-6 py-8">
            {children}
          </div>
        </main>
      </body>
    </html>
  )
}
