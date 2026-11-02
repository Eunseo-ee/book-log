'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useAuth } from '@/components/auth/AuthProvider'
import AuthCard, { SubmitButton, TextField } from '@/components/auth/AuthCard'
import { toErrorMessages } from '@/lib/api'
import { validateLogin, type FieldErrors } from '@/lib/authValidation'

export default function LoginPage() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [errors, setErrors] = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (submitting) return
    const invalid = validateLogin(username, password)
    setFieldErrors(invalid)
    setErrors([])
    if (Object.keys(invalid).length > 0) return

    setSubmitting(true)
    try {
      // 성공하면 store가 authenticated로 바뀌고 AuthProvider가 메인으로 보낸다
      await login({ username: username.trim(), password })
    } catch (err) {
      setErrors(toErrorMessages(err))
      setSubmitting(false)
    }
  }

  return (
    <AuthCard
      title="로그인"
      footer={
        <>
          계정이 없나요?{' '}
          <Link href="/signup" className="text-violet-600 hover:underline">
            회원가입
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-3">
        <TextField
          id="username"
          label="아이디"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          error={fieldErrors.username}
          disabled={submitting}
        />
        <TextField
          id="password"
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          disabled={submitting}
        />
        {errors.length > 0 && (
          <div role="alert" className="text-[13px] text-red-500">
            {errors.map((m) => (
              <p key={m}>{m}</p>
            ))}
          </div>
        )}
        <SubmitButton submitting={submitting}>{submitting ? '로그인 중...' : '로그인'}</SubmitButton>
      </form>
    </AuthCard>
  )
}
