'use client'

import { usePathname } from 'next/navigation'
import { isPublicPath, useAuth } from '@/components/auth/AuthProvider'
import Sidebar from '@/components/sidebar/Sidebar'
import Header from '@/components/layout/Header'

// 경로/로그인 상태에 맞는 화면만 그린다. 리다이렉트는 AuthProvider가 하고, 여기서는 보호 화면이 순간적으로 비치지 않게 막는다
export default function AppShell({ children }: { children: React.ReactNode }) {
  const { status } = useAuth()
  const pathname = usePathname()
  const isPublic = isPublicPath(pathname)

  if (status === 'loading') {
    return <div className="min-h-screen flex items-center justify-center text-sm text-neutral-400">불러오는 중...</div>
  }

  if (isPublic) {
    // 로그인 상태에서 /login 접근 시 메인으로 이동하는 동안 폼을 보여주지 않는다
    return status === 'authenticated' ? null : <>{children}</>
  }

  // 비로그인 상태의 보호 페이지는 /login으로 이동하는 동안 아무것도 그리지 않는다 (API 호출도 막힌다)
  if (status !== 'authenticated') return null

  return (
    <>
      {/* 사이드바는 왼쪽 고정 */}
      <Sidebar />

      {/* 사이드바 너비를 제외한 나머지 영역 */}
      <div className="ml-56 flex flex-col min-h-screen">
        <Header />

        {/* 실제 페이지 내용 */}
        <main className="flex-1">
          <div className="max-w-4xl mx-auto px-6 py-8">{children}</div>
        </main>
      </div>
    </>
  )
}
