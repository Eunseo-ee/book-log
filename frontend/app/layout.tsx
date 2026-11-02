import type { Metadata } from 'next'
import './globals.css'
import AuthProvider from '@/components/auth/AuthProvider'
import AppShell from '@/components/layout/AppShell'

export const metadata: Metadata = {
  title: 'Archivio',
  description: '나만의 콘텐츠 기록 아카이브',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body className="bg-neutral-50 dark:bg-neutral-950 text-neutral-900 dark:text-white antialiased">
        <AuthProvider>
          <AppShell>{children}</AppShell>
        </AuthProvider>
      </body>
    </html>
  )
}