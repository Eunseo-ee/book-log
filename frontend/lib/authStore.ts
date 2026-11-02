// 인증 상태의 단일 저장소.
// - access 토큰: 모듈 변수(메모리)에만 둔다. 새로고침하면 사라지고, 앱 시작 시 refresh로 복구한다.
// - refresh 토큰: 백엔드가 응답 body로 내려주므로 localStorage에 둔다 (XSS 위험은 decisions.md 참고).
// api.ts(토큰 첨부/재발급)와 AuthProvider(화면/라우팅)가 같은 저장소를 본다.

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthSnapshot {
  status: AuthStatus
  username: string | null
}

const REFRESH_KEY = 'archivio.refreshToken'

let accessToken: string | null = null
// 서버 렌더링/하이드레이션용 고정 스냅샷. 호출마다 새 객체를 만들면 React가 무한 루프로 판단한다
const SERVER_SNAPSHOT: AuthSnapshot = { status: 'loading', username: null }
let snapshot: AuthSnapshot = SERVER_SNAPSHOT
// clearSession/setSession마다 올라간다. 진행 중이던 refresh가 로그아웃 뒤에 세션을 되살리지 못하게 막는 용도
let generation = 0
const listeners = new Set<() => void>()

function emit(next: AuthSnapshot) {
  // 값이 같으면 기존 참조를 유지한다 (스냅샷은 상태가 바뀔 때만 새 객체)
  if (next.status === snapshot.status && next.username === snapshot.username) return
  snapshot = next
  listeners.forEach((l) => l())
}

export function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export const getSnapshot = () => snapshot
// 서버 렌더링 시에는 항상 loading (토큰은 브라우저에만 있다)
export const getServerSnapshot = (): AuthSnapshot => SERVER_SNAPSHOT

export const getAccessToken = () => accessToken
export const getGeneration = () => generation

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_KEY)
  } catch {
    return null
  }
}

function writeRefreshToken(token: string | null) {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token)
    else localStorage.removeItem(REFRESH_KEY)
  } catch {
    // 저장소 접근이 막힌 환경(시크릿 모드 등)에서는 새로고침 후 로그인이 풀릴 뿐이다
  }
}

// access 토큰의 payload에서 username claim만 꺼낸다 (서명 검증은 서버 몫이고, 화면 표시용 값이다)
function usernameFromToken(token: string): string | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = new TextDecoder().decode(Uint8Array.from(atob(payload), (c) => c.charCodeAt(0)))
    const username = (JSON.parse(json) as { username?: unknown }).username
    return typeof username === 'string' ? username : null
  } catch {
    return null
  }
}

// 로그인 성공: 두 토큰 모두 교체
export function setSession(access: string, refresh: string, username: string) {
  generation++
  accessToken = access
  writeRefreshToken(refresh)
  emit({ status: 'authenticated', username })
}

// refresh 성공: access만 교체 (백엔드는 refresh를 회전하지 않는다)
export function setAccessToken(access: string) {
  accessToken = access
  emit({ status: 'authenticated', username: usernameFromToken(access) ?? snapshot.username })
}

// 로그아웃/refresh 실패: 토큰을 모두 지우고 비로그인 상태로
export function clearSession() {
  generation++
  accessToken = null
  writeRefreshToken(null)
  emit({ status: 'unauthenticated', username: null })
}
