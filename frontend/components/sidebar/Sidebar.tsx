'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { BookOpen, Calendar, Home, Highlighter } from 'lucide-react'
import MiniCalendar from './MiniCalendar'

const NAV = [
  { href: '/',            label: '홈',           icon: Home        },
  { href: '/calendar',    label: '캘린더',        icon: Calendar    },
  { href: '/highlights',  label: '나만의 문장 모음집', icon: Highlighter },
]

export default function Sidebar() {
  const pathname = usePathname()

  return (
    <aside className="fixed left-0 top-0 h-screen w-56 flex flex-col bg-white dark:bg-neutral-950 border-r border-neutral-200 dark:border-neutral-800 z-30">
      {/* 로고 */}
      <div className="flex items-center gap-2 px-5 py-5 border-b border-neutral-100 dark:border-neutral-800">
        <BookOpen size={18} className="text-violet-600" />
        <span className="text-[15px] font-semibold tracking-tight text-neutral-900 dark:text-white">
          Archivio
        </span>
      </div>

      {/* 메뉴 */}
      <nav className="px-2 pt-3 flex flex-col gap-[2px]">
        {NAV.map(({ href, label, icon: Icon }) => {
          const active = pathname === href
          return (
            <Link
              key={href}
              href={href}
              className={`flex items-center gap-3 px-3 py-2 rounded-lg text-[13px] transition-colors
                ${active
                  ? 'bg-violet-50 dark:bg-violet-950 text-violet-700 dark:text-violet-300 font-medium'
                  : 'text-neutral-500 dark:text-neutral-400 hover:bg-neutral-50 dark:hover:bg-neutral-900 hover:text-neutral-800 dark:hover:text-neutral-200'
                }`}
            >
              <Icon size={15} />
              {label}
            </Link>
          )
        })}
      </nav>

      <div className="mx-3 my-3 border-t border-neutral-100 dark:border-neutral-800" />

      {/* 미니 캘린더 — 경량 boolean 쿼리 사용 */}
      <MiniCalendar />
    </aside>
  )
}
