import { BookOpen } from 'lucide-react'

// 로그인/회원가입 화면 공통 틀 (사이드바 없이 가운데 카드)
export default function AuthCard({
  title,
  children,
  footer,
}: {
  title: string
  children: React.ReactNode
  footer: React.ReactNode
}) {
  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      <div className="w-full max-w-sm bg-white dark:bg-neutral-950 border border-neutral-200 dark:border-neutral-800 rounded-2xl p-6">
        <div className="flex items-center gap-2 mb-5">
          <BookOpen size={18} className="text-violet-600" />
          <span className="text-[15px] font-semibold tracking-tight">Archivio</span>
        </div>
        <h1 className="text-lg font-semibold text-neutral-900 dark:text-white mb-4">{title}</h1>
        {children}
        <p className="mt-5 text-center text-[13px] text-neutral-500">{footer}</p>
      </div>
    </div>
  )
}

export function TextField({
  id,
  label,
  error,
  ...input
}: { id: string; label: string; error?: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-[13px] font-medium text-neutral-700 dark:text-neutral-300">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className="px-3 py-2 text-sm rounded-lg border border-neutral-200 dark:border-neutral-700 bg-white dark:bg-neutral-900 focus:outline-none focus:ring-2 focus:ring-violet-500 disabled:opacity-60"
        {...input}
      />
      {error && (
        <p id={`${id}-error`} className="text-[12px] text-red-500">
          {error}
        </p>
      )}
    </div>
  )
}

export function SubmitButton({ submitting, children }: { submitting: boolean; children: React.ReactNode }) {
  return (
    <button
      type="submit"
      disabled={submitting}
      className="mt-1 px-4 py-2 text-sm font-medium text-white bg-violet-600 hover:bg-violet-700 rounded-lg transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
    >
      {children}
    </button>
  )
}
