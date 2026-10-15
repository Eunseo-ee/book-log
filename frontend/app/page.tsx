export default function HomePage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold text-neutral-900 dark:text-white">홈</h1>
        <p className="text-sm text-neutral-400 mt-1">나의 콘텐츠 기록</p>
      </div>
      <p className="text-sm text-neutral-500">
        사이드바에서 캘린더나 나만의 문장 모음집으로 이동하세요.
      </p>
    </div>
  )
}
