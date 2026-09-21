<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as authApi from '@/api/auth'
import { useResendCooldown } from '@/composables/useResendCooldown'
import PasswordToggleButton from '@/components/PasswordToggleButton.vue'
import { isValidEmail, isValidNickname, isValidPassword } from '@/utils/validators'
import type { AxiosError } from 'axios'
import type { ApiErrorBody } from '@/types/common'

const router = useRouter()

// ---- 이메일 중복확인 / 인증 ----
const email = ref('')
const emailChecked = ref(false)
const emailCheckError = ref('')
const codeRequested = ref(false)
const verifyCode = ref('')
const verifyCodeError = ref(false)
const emailVerified = ref(false)
const emailVerifiedToken = ref('')

const { cooldown: resendCooldown, start: startResendTimer } = useResendCooldown(30)

function onEmailInput() {
  // 이메일을 다시 수정하면 이전 인증 상태를 모두 초기화한다 —
  // 검증된 이메일과 실제 가입 이메일이 달라지는 것을 방지 (mock/signup.html과 동일 정책)
  emailChecked.value = false
  emailCheckError.value = ''
  codeRequested.value = false
  emailVerified.value = false
  emailVerifiedToken.value = ''
}

const isCheckingEmail = ref(false)

async function checkEmail() {
  if (!email.value || isCheckingEmail.value) return

  if (!isValidEmail(email.value)) {
    emailChecked.value = false
    emailCheckError.value = '올바른 이메일 형식이 아니에요'
    return
  }

  isCheckingEmail.value = true
  try {
    const { data } = await authApi.checkEmail(email.value)
    emailChecked.value = data.available
    emailCheckError.value = data.available ? '' : '이미 사용 중인 이메일이에요'
  } catch {
    emailCheckError.value = '확인 중 오류가 발생했어요. 잠시 후 다시 시도해주세요'
  } finally {
    isCheckingEmail.value = false
  }
}

const isSendingCode = ref(false)

async function sendVerificationCode(): Promise<boolean> {
  if (isSendingCode.value) return false
  isSendingCode.value = true
  try {
    await authApi.requestSignupVerification(email.value)
    codeRequested.value = true
    verifyCode.value = ''
    return true
  } catch (e) {
    if ((e as AxiosError<ApiErrorBody>).response?.data?.code === 'EMAIL_ALREADY_EXISTS') {
      emailChecked.value = false
      emailCheckError.value = '이미 사용 중인 이메일이에요'
    }
    return false
  } finally {
    isSendingCode.value = false
  }
}

function onVerifyCodeInput(event: Event) {
  verifyCode.value = (event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6)
  verifyCodeError.value = false
}

const canConfirmCode = computed(() => /^\d{6}$/.test(verifyCode.value))

async function confirmVerificationCode() {
  if (!canConfirmCode.value) return
  try {
    const { data } = await authApi.confirmSignupVerification(email.value, verifyCode.value)
    emailVerifiedToken.value = data.emailVerifiedToken
    emailVerified.value = true
    codeRequested.value = false
  } catch {
    verifyCodeError.value = true
  }
}

async function resendCode() {
  if (resendCooldown.value > 0) return

  if (await sendVerificationCode()) {
    startResendTimer()
  }
}

// ---- 닉네임 / 비밀번호 ----
const nickname = ref('')
const nicknameTouched = ref(false)
const password = ref('')
const passwordConfirm = ref('')
const showPassword = ref(false)
const showPasswordConfirm = ref(false)

const nicknameValid = computed(() => isValidNickname(nickname.value))
const passwordLengthValid = computed(() => password.value.length === 0 || isValidPassword(password.value))
const passwordsMatch = computed(() => passwordConfirm.value.length === 0 || password.value === passwordConfirm.value)

// ---- 약관 동의 ----
const agreements = ref({ terms: false, privacy: false, location: false, marketing: false })
const termsTouched = ref(false)
const requiredAgreed = computed(() => agreements.value.terms && agreements.value.privacy && agreements.value.location)
const allAgreed = computed({
  get: () => Object.values(agreements.value).every(Boolean),
  set: (value: boolean) => {
    agreements.value = { terms: value, privacy: value, location: value, marketing: value }
  },
})

// ---- 제출 ----
const isSubmitting = ref(false)
const submitError = ref('')

const canSubmit = computed(
  () =>
    emailVerified.value &&
    nicknameValid.value &&
    isValidPassword(password.value) &&
    password.value === passwordConfirm.value &&
    requiredAgreed.value,
)

async function handleSubmit() {
  nicknameTouched.value = true
  termsTouched.value = true
  if (!canSubmit.value || isSubmitting.value) return

  isSubmitting.value = true
  submitError.value = ''
  try {
    await authApi.signup({
      email: email.value,
      nickname: nickname.value.trim(),
      password: password.value,
      emailVerifiedToken: emailVerifiedToken.value,
    })
    // 가입은 자동 로그인이 아니다(§2) — 로그인 화면으로 이동해 다시 로그인하게 한다
    router.push({ path: '/login', query: { signup: 'success' } })
  } catch (e) {
    const code = (e as AxiosError<ApiErrorBody>).response?.data?.code
    if (code === 'EMAIL_NOT_VERIFIED') {
      submitError.value = '이메일 인증이 만료됐어요. 처음부터 다시 진행해주세요'
      emailVerified.value = false
    } else if (code === 'EMAIL_ALREADY_EXISTS') {
      submitError.value = '이미 가입된 이메일이에요'
      emailChecked.value = false
    } else if (code === 'DUPLICATE_NICKNAME') {
      submitError.value = '이미 사용중인 닉네임이에요'
      nicknameTouched.value = true
    } else {
      submitError.value = '가입 중 오류가 발생했어요. 잠시 후 다시 시도해주세요'
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="bg-white lg:bg-neutral-50 lg:flex lg:items-start lg:justify-center lg:py-12 min-h-screen">
    <div
      class="relative min-h-screen w-full lg:min-h-0 lg:w-full lg:max-w-[440px] bg-white lg:rounded-2xl lg:border lg:border-hairline lg:shadow-floating overflow-hidden flex flex-col"
    >
      <header class="shrink-0 flex items-center gap-3 px-4 py-3.5">
        <router-link to="/login" aria-label="뒤로가기" class="w-8 h-8 flex items-center justify-center -ml-1 rounded-full hover:bg-black/5 transition-colors">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#222222" stroke-width="2.2" stroke-linecap="round"><path d="M15 18l-6-6 6-6"/></svg>
        </router-link>
        <h1 class="text-[16px] font-bold text-ink">회원가입</h1>
      </header>

      <div class="flex-1 overflow-y-auto px-5 pb-8 lg:flex-none lg:overflow-visible">
        <form class="flex flex-col gap-4 mt-2" @submit.prevent="handleSubmit">
          <!-- 이메일 -->
          <div>
            <label class="text-[13px] font-semibold text-ink">이메일</label>
            <div class="mt-1.5 flex gap-2">
              <input v-model="email" @input="onEmailInput" type="email" placeholder="you@example.com"
                class="flex-1 min-w-0 rounded-xl border border-hairline px-3.5 py-3 text-[14px] outline-none focus:border-primary" />
              <button type="button" @click="checkEmail" :disabled="emailChecked || isCheckingEmail"
                class="shrink-0 rounded-xl border border-navborder text-ink text-[13px] font-bold px-3.5 disabled:opacity-50 hover:bg-neutral-50 hover:border-ink transition-colors">
                {{ isCheckingEmail ? '확인 중...' : '중복확인' }}
              </button>
            </div>
            <p v-if="emailChecked" class="text-[12px] font-semibold text-primary mt-1.5">사용 가능한 이메일이에요</p>
            <p v-else-if="emailCheckError" class="text-[12px] font-semibold text-red-500 mt-1.5">{{ emailCheckError }}</p>

            <button v-if="emailChecked && !codeRequested && !emailVerified" type="button" :disabled="isSendingCode"
              @click="sendVerificationCode" class="mt-2 text-[12px] font-semibold text-primary hover:underline transition-colors disabled:text-muted-2">
              {{ isSendingCode ? '발송 중...' : '인증코드 받기' }}
            </button>

            <div v-if="codeRequested" class="mt-2.5 rounded-xl border border-hairline p-3">
              <p class="text-[12px] text-muted-2">이메일로 받은 6자리 인증코드를 입력해주세요</p>
              <div class="flex gap-2 mt-2">
                <input :value="verifyCode" @input="onVerifyCodeInput" type="text" inputmode="numeric" maxlength="6"
                  placeholder="6자리 숫자"
                  class="flex-1 min-w-0 rounded-xl border border-hairline px-3.5 py-2.5 text-[14px] tracking-[0.3em] outline-none focus:border-primary" />
                <button type="button" :disabled="!canConfirmCode" @click="confirmVerificationCode"
                  :class="canConfirmCode ? 'bg-primary hover:bg-primary/90' : 'bg-navborder'"
                  class="shrink-0 rounded-xl text-white text-[13px] font-bold px-3.5 transition-colors">인증확인</button>
              </div>
              <p v-if="verifyCodeError" class="text-[12px] font-semibold text-red-500 mt-1.5">인증코드가 일치하지 않아요</p>
              <button type="button" :disabled="resendCooldown > 0" @click="resendCode"
                class="mt-2 text-[12px] font-semibold text-primary disabled:text-muted-2 hover:underline transition-colors">
                {{ resendCooldown > 0 ? `${resendCooldown}초 후 다시 시도할 수 있어요` : '코드를 받지 못하셨나요? 재발송' }}
              </button>
            </div>

            <p v-if="emailVerified" class="mt-2 flex items-center gap-1.5 text-[12px] font-semibold text-primary">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
              이메일 인증이 완료됐어요
            </p>
          </div>

          <!-- 닉네임 -->
          <div>
            <label class="text-[13px] font-semibold text-ink">닉네임</label>
            <input v-model="nickname" @blur="nicknameTouched = true" type="text" placeholder="다른 사용자에게 보여질 이름"
              class="mt-1.5 w-full rounded-xl border border-hairline px-3.5 py-3 text-[14px] outline-none focus:border-primary" />
            <p v-if="nicknameTouched && !nicknameValid" class="text-[12px] font-semibold text-red-500 mt-1.5">닉네임은 2~12자여야 해요</p>
          </div>

          <!-- 비밀번호 -->
          <div>
            <label class="text-[13px] font-semibold text-ink">비밀번호</label>
            <div class="mt-1.5 relative">
              <input v-model="password" :type="showPassword ? 'text' : 'password'" placeholder="8~64자, 영문/숫자/특수문자"
                class="w-full rounded-xl border border-hairline px-3.5 py-3 pr-11 text-[14px] outline-none focus:border-primary" />
              <PasswordToggleButton :visible="showPassword" @toggle="showPassword = !showPassword" />
            </div>
            <p v-if="!passwordLengthValid" class="text-[12px] font-semibold text-red-500 mt-1.5">비밀번호는 8~64자, 영문/숫자/특수문자만 사용할 수 있어요</p>
          </div>

          <!-- 비밀번호 확인 -->
          <div>
            <label class="text-[13px] font-semibold text-ink">비밀번호 확인</label>
            <div class="mt-1.5 relative">
              <input v-model="passwordConfirm" :type="showPasswordConfirm ? 'text' : 'password'" placeholder="비밀번호를 다시 입력해주세요"
                class="w-full rounded-xl border border-hairline px-3.5 py-3 pr-11 text-[14px] outline-none focus:border-primary" />
              <PasswordToggleButton :visible="showPasswordConfirm" @toggle="showPasswordConfirm = !showPasswordConfirm" />
            </div>
            <p v-if="!passwordsMatch" class="text-[12px] font-semibold text-red-500 mt-1.5">비밀번호가 일치하지 않아요</p>
          </div>

          <!-- 약관 동의 -->
          <div class="mt-2 rounded-2xl border border-hairline p-4">
            <label class="flex items-center gap-2.5">
              <input v-model="allAgreed" type="checkbox" class="w-5 h-5 rounded accent-primary" />
              <span class="text-[14px] font-bold text-ink">약관 전체 동의</span>
            </label>
            <div class="h-px bg-hairline my-3"></div>
            <div class="flex flex-col gap-2.5">
              <label class="flex items-center justify-between">
                <span class="flex items-center gap-2.5">
                  <input v-model="agreements.terms" @change="termsTouched = true" type="checkbox" class="w-4.5 h-4.5 rounded accent-primary" />
                  <span class="text-[13px] text-ink">(필수) 서비스 이용약관</span>
                </span>
                <a href="#" class="text-[12px] text-muted-2 underline hover:text-ink transition-colors">보기</a>
              </label>
              <label class="flex items-center justify-between">
                <span class="flex items-center gap-2.5">
                  <input v-model="agreements.privacy" @change="termsTouched = true" type="checkbox" class="w-4.5 h-4.5 rounded accent-primary" />
                  <span class="text-[13px] text-ink">(필수) 개인정보 처리방침</span>
                </span>
                <a href="#" class="text-[12px] text-muted-2 underline hover:text-ink transition-colors">보기</a>
              </label>
              <label class="flex items-center justify-between">
                <span class="flex items-center gap-2.5">
                  <input v-model="agreements.location" @change="termsTouched = true" type="checkbox" class="w-4.5 h-4.5 rounded accent-primary" />
                  <span class="text-[13px] text-ink">(필수) 위치기반서비스 이용약관</span>
                </span>
                <a href="#" class="text-[12px] text-muted-2 underline hover:text-ink transition-colors">보기</a>
              </label>
              <label class="flex items-center justify-between">
                <span class="flex items-center gap-2.5">
                  <input v-model="agreements.marketing" type="checkbox" class="w-4.5 h-4.5 rounded accent-primary" />
                  <span class="text-[13px] text-ink">(선택) 마케팅 정보 수신</span>
                </span>
                <a href="#" class="text-[12px] text-muted-2 underline hover:text-ink transition-colors">보기</a>
              </label>
            </div>
          </div>
          <p v-if="termsTouched && !requiredAgreed" class="text-[12px] font-semibold text-red-500 -mt-2">필수 약관에 모두 동의해주세요</p>

          <p v-if="submitError" class="text-[12px] font-semibold text-red-500 -mt-2">{{ submitError }}</p>

          <button type="submit" :disabled="!canSubmit || isSubmitting"
            :class="canSubmit && !isSubmitting ? 'bg-primary hover:bg-primary/90' : 'bg-navborder'"
            class="mt-2 w-full rounded-xl text-white text-[15px] font-bold py-3.5 transition-colors">
            {{ isSubmitting ? '가입 처리 중...' : '가입하기' }}
          </button>
        </form>
      </div>
    </div>
  </div>
</template>
