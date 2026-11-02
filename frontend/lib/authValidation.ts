// 백엔드 LoginRequestDto/RegisterRequestDto의 제약과 맞춘 입력 검증 (서버 검증을 대체하지 않고 왕복을 줄이는 용도)
// 각 함수는 필드명 → 메시지 맵을 돌려주고, 비어 있으면 통과

export type FieldErrors = Partial<Record<'username' | 'password' | 'email', string>>

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const utf8Bytes = (value: string) => new TextEncoder().encode(value).length

export function validateLogin(username: string, password: string): FieldErrors {
  const errors: FieldErrors = {}
  if (!username.trim()) errors.username = '아이디를 입력해주세요.'
  else if (username.length > 20) errors.username = '아이디는 20자 이내로 입력해주세요.'
  if (!password) errors.password = '비밀번호를 입력해주세요.'
  else if (password.length > 64) errors.password = '비밀번호는 64자 이내로 입력해주세요.'
  return errors
}

export function validateRegister(username: string, password: string, email: string): FieldErrors {
  const errors: FieldErrors = {}
  if (!username.trim()) errors.username = '아이디를 입력해주세요.'
  else if (username.length < 4 || username.length > 20) errors.username = '아이디는 4~20자여야 합니다.'

  if (!password) errors.password = '비밀번호를 입력해주세요.'
  else if (password.length < 8 || password.length > 64) errors.password = '비밀번호는 8~64자여야 합니다.'
  // BCrypt는 72바이트 이후를 무시하므로 서버도 막는다 (한글은 1글자 3바이트라 24자까지)
  else if (utf8Bytes(password) > 72) errors.password = '비밀번호가 너무 깁니다. 영문·숫자·기호는 72자, 한글은 24자까지 가능합니다.'

  if (!email.trim()) errors.email = '이메일을 입력해주세요.'
  else if (!EMAIL_PATTERN.test(email.trim())) errors.email = '이메일 형식이 올바르지 않습니다.'
  else if (email.trim().length > 100) errors.email = '이메일은 100자 이내로 입력해주세요.'
  return errors
}
