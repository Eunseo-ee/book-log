'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useAuth } from '@/components/auth/AuthProvider'
import AuthCard, { SubmitButton, TextField } from '@/components/auth/AuthCard'
import { register, toErrorMessages } from '@/lib/api'
import { validateRegister, type FieldErrors } from '@/lib/authValidation'

export default function SignupPage() {
  const { login } = useAuth()
  const router = useRouter()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [errors, setErrors] = useState<string[]>([])
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (submitting) return
    const invalid = validateRegister(username, password, email)
    setFieldErrors(invalid)
    setErrors([])
    if (Object.keys(invalid).length > 0) return

    setSubmitting(true)
    try {
      await register({ username: username.trim(), password, email: email.trim() })
    } catch (err) {
      // 400(@Valid 맵), 409(아이디 중복) 메시지를 그대로 보여준다
      setErrors(toErrorMessages(err))
      setSubmitting(false)
      return
    }
    try {
      // 가입 직후 같은 정보로 로그인한다. 성공하면 AuthProvider가 메인으로 보낸다
      await login({ username: username.trim(), password })
    } catch {
      // 가입은 됐으므로 로그인 화면에서 직접 로그인하게 한다
      router.replace('/login')
    }
  }

  return (
    <AuthCard
      title="회원가입"
      footer={
        <>
          이미 계정이 있나요?{' '}
          <Link href="/login" className="text-violet-600 hover:underline">
            로그인
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-3">
        <TextField
          id="username"
          label="아이디 (4~20자)"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          error={fieldErrors.username}
          disabled={submitting}
        />
        <TextField
          id="email"
          label="이메일"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          disabled={submitting}
        />
        <TextField
          id="password"
          label="비밀번호 (8~64자)"
          type="password"
          autoComplete="new-password"
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
        <SubmitButton submitting={submitting}>{submitting ? '가입 중...' : '가입하기'}</SubmitButton>
      </form>
    </AuthCard>
  )
}
