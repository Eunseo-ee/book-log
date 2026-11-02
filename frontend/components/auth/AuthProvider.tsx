'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useSyncExternalStore } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { login as loginRequest, refreshAccessToken } from '@/lib/api'
import {
  clearSession,
  getRefreshToken,
  getServerSnapshot,
  getSnapshot,
  setSession,
  subscribe,
  type AuthStatus,
} from '@/lib/authStore'
import type { LoginRequestDto } from '@/types'

const PUBLIC_PATHS = ['/login', '/signup']
export const isPublicPath = (pathname: string) => PUBLIC_PATHS.includes(pathname)

interface AuthContextValue {
  status: AuthStatus
  username: string | null
  login: (dto: LoginRequestDto) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있습니다.')
  return ctx
}

// 인증 상태는 lib/authStore가 단일 출처이고, 이 컴포넌트는 그것을 구독해 context로 내려주며 라우팅을 맡는다
export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const { status, username } = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

  // 앱 시작: access 토큰은 메모리라 새로고침하면 사라지므로 refresh 토큰으로 복구한다
  useEffect(() => {
    if (getSnapshot().status !== 'loading') return
    if (!getRefreshToken()) {
      clearSession()
      return
    }
    void refreshAccessToken() // 성공하면 authenticated, 실패하면 store가 unauthenticated로 바꾼다
  }, [])

  // 비로그인 → 보호 페이지는 로그인으로, 로그인 상태 → 로그인/가입 페이지는 메인으로
  useEffect(() => {
    if (status === 'unauthenticated' && !isPublicPath(pathname)) router.replace('/login')
    else if (status === 'authenticated' && isPublicPath(pathname)) router.replace('/')
  }, [status, pathname, router])

  const login = useCallback(async (dto: LoginRequestDto) => {
    const res = await loginRequest(dto)
    setSession(res.accessToken, res.refreshToken, res.username)
  }, [])

  const logout = useCallback(() => clearSession(), [])

  const value = useMemo(() => ({ status, username, login, logout }), [status, username, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
