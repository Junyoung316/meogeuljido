export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 64
const PASSWORD_ALLOWED_PATTERN = /^[A-Za-z0-9!@#$%^&*()_+=,.?<>~:;'"|-]*$/

export const NICKNAME_MIN_LENGTH = 2
export const NICKNAME_MAX_LENGTH = 12

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function isValidEmail(email: string): boolean {
  return EMAIL_PATTERN.test(email)
}

export function isValidPassword(password: string): boolean {
  return (
    password.length >= PASSWORD_MIN_LENGTH &&
    password.length <= PASSWORD_MAX_LENGTH &&
    PASSWORD_ALLOWED_PATTERN.test(password)
  )
}

export function isValidNickname(nickname: string): boolean {
  const trimmed = nickname.trim()
  return trimmed.length >= NICKNAME_MIN_LENGTH && trimmed.length <= NICKNAME_MAX_LENGTH
}
