<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'
import { useResendCooldown } from '@/composables/useResendCooldown'
import PasswordToggleButton from '@/components/PasswordToggleButton.vue'
import type { AxiosError } from 'axios'
import type { ApiErrorBody } from '@/types/common'

type Step = 'login' | 'locked' | 'unlock-code' | 'unlock-complete'

const router = useRouter()
const authStore = useAuthStore()

const step = ref<Step>('login')
const email = ref('')
const password = ref('')
const rememberMe = ref(false)
const showPassword = ref(false)
const loginError = ref('')
const isSubmitting = ref(false)

async function handleLogin() {
  if (isSubmitting.value) return
  isSubmitting.value = true
  loginError.value = ''

  try {
    await authStore.login (
      email.value,
      password.value,
      rememberMe.value
    )
    router.push(
      // TODO(admin 화면 구현 후 교체): /admin/restaurants 라우트가 아직 없어 일단 홈으로 보냄
      // authStore.user?.role === 'ADMIN' ? '/admin/restaurants' : '/'
      '/'
    )
  } catch (e) {
    const code = (e as AxiosError<ApiErrorBody>).response?.data?.code

    if (code === 'LOGIN_LOCKED') {
      step.value = 'locked'
    } else {
      /**
       * 이메일 존재 여부를 노출하지 않기 위해 INVALID_CREDENTIALS 등은 문구를 하나로 통일
       */
      loginError.value = '이메일 또는 비밀번호가 올바르지 않습니다.'
    }
  } finally {
    isSubmitting.value = false
  }
}

const isRequestingUnlock = ref(false)

async function requestUnlock() {
  if (isRequestingUnlock.value) return
  isRequestingUnlock.value = true
  try {
    await authApi.requestLoginUnlock(email.value)
    step.value = 'unlock-code'
  } catch {
    /**
     * 429 TOO_MANY_REQUESTS 등 - 잠금 화면에 머무르게 두고 별도 안내는 생략(재시도 유도)
     */
  } finally {
    isRequestingUnlock.value = false
  }
}

const unlockCode = ref('')
const unlockCodeError = ref(false)
const canSubmitUnlockCode = computed(() => /^\d{6}$/.test(unlockCode.value))

function onUnlockCodeInput(event: Event) {
  unlockCode.value = (event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6)
  unlockCodeError.value = false
}

async function submitUnlockCode() {
  if (!canSubmitUnlockCode.value) return

  try {
    await authApi.confirmLoginUnlock(
      email.value,
      unlockCode.value
    )
    step.value = 'unlock-complete'
  } catch (e) {
    unlockCodeError.value = (e as AxiosError<ApiErrorBody>).response?.data?.code === 'CODE_MISMATCH'
  }
}

const { cooldown: resendCooldown, start: startResendTimer } = useResendCooldown(30)

async function resendUnlockCode() {
  if (resendCooldown.value > 0) return

  try {
    await authApi.requestLoginUnlock(email.value)
    startResendTimer()
  } catch {
    // 429(쿨다운) 등 실패 시 별도 안내 없이 재시도 유도 - 기존 정책 유지
  }
}

function backToLogin() {
  step.value = 'login'
  password.value = ''
  loginError.value = ''
}

</script>

<template>
  <!-- 인증 화면은 헤더 네비게이션 없이, 데스크톱에서는 중앙 정렬된 카드로만 확장 -->
  <div class="bg-white lg:bg-neutral-50 lg:flex lg:items-center lg:justify-center min-h-screen">
    <div class="relative min-h-screen w-full lg:min-h-0 lg:w-full lg:max-w-[420px] bg-white lg:rounded-2xl lg:border lg:border-hairline lg:shadow-floating overflow-hidden flex flex-col">
      <router-link to="/" aria-label="닫기" class="absolute top-4 left-4 z-10 w-9 h-9 flex items-center justify-center rounded-full hover:bg-black/5 transition-colors">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#222222" stroke-width="2.2" stroke-linecap="round">
        <path d="M18 6 6 18M6 6l12 12"/>
      </svg>
      </router-link>

      <div class="flex-1 flex flex-col justify-center px-6 lg:pt-16 lg:pb-8">
        <!-- ============ 로그인 폼 ============ -->
        <section v-if="step === 'login'">
          <div class="mb-8">
            <p class="text-[24px] font-bold text-ink">먹을지도</p>
            <p class="text-[13px] text-muted-2 mt-1.5">오늘 뭐 먹지? 고민은 그만, 아무거나 먹으러 가자</p>
          </div>

          <form class="flex flex-col gap-3" @submit.prevent="handleLogin">
            <div>
              <label class="text-[13px] font-semibold text-ink">
                이메일
              </label>
              <input v-model="email" type="email" required placeholder="you@example.com"
                class="mt-1.5 w-full rounded-xl border border-hairline px-3.5 py-3 text-[14px] outline-none focus:border-primary" />
            </div>

            <div>
              <label class="text-[13px] font-semibold text-ink">
                비밀번호
              </label>
              <div class="mt-1.5 relative">
                <input v-model="password" :type="showPassword ? 'text' : 'password'" required placeholder="비밀번호를 입력해주세요" class="w-full rounded-xl border border-hairline px-3.5 py-3 pr-11 text-[14px] outline-none focus:border-primary" />
                <PasswordToggleButton :visible="showPassword" @toggle="showPassword = !showPassword" />
              </div>
              <p v-if="loginError" class="text-[12px] font-semibold text-red-500 mt-1.5">{{ loginError }}</p>
            </div>

            <div class="flex items-center justify-between mt-1">
              <label class="flex items-center gap-2">
                <input v-model="rememberMe" type="checkbox" class="w-4 h-4 rounded accent-primary" />
                <span class="text-[12px] text-muted-2">로그인 상태 유지</span>
              </label>
              <span class="text-[12px] text-muted cursor-not-allowed" aria-disabled="true">
                비밀번호를 잊으셨나요?
              </span>
            </div>

            <button type="submit" :disabled="isSubmitting"
              class="mt-2 w-full rounded-xl bg-primary text-white text-[15px] font-bold py-3.5 disabled:opacity-60 hover:bg-primary/90 transition-colors">
              {{ isSubmitting ? '로그인 중...' : '로그인' }}
            </button>
          </form>

          <div class="flex items-center gap-3 my-6">
            <div class="flex-1 h-px bg-hairline"></div>
            <span class="text-[12px] text-muted-2">또는</span>
            <div class="flex-1 h-px bg-hairline"></div>
          </div>

          <p class="text-center text-[13px] text-muted-2">
            아직 계정이 없으신가요?
            <router-link to="/signup" class="text-primary font-bold hover:underline">회원가입</router-link>
          </p>
        </section>

        <!-- ============ 잠금 상태 ============ -->
        <section v-else-if="step === 'locked'">
          <div class="flex flex-col items-center text-center pt-2 pb-1">
            <div class="w-14 h-14 rounded-full bg-red-50 flex items-center justify-center">
              <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#ef4444" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></svg>
            </div>
            <p class="text-[18px] font-bold text-ink mt-4">로그인이 잠겼어요</p>
            <p class="text-[13px] text-muted-2 mt-1.5 leading-relaxed">
              비밀번호를 5회 연속 틀려 계정이 잠겼어요.<br />시간이 지나도 자동으로 풀리지 않아요 — 이메일 인증 후 다시 로그인할 수 있어요.
            </p>
          </div>

          <div class="mt-6 rounded-2xl bg-neutral-100 px-4 py-3.5">
            <p class="text-[12px] text-muted-2">인증코드를 받을 이메일</p>
            <p class="text-[14px] font-semibold text-ink mt-1">{{ email }}</p>
          </div>

          <button type="button" :disabled="isRequestingUnlock" @click="requestUnlock"
            class="mt-6 w-full rounded-xl bg-primary text-white text-[15px] font-bold py-3.5 hover:bg-primary/90 transition-colors disabled:opacity-60">
            {{ isRequestingUnlock ? '발송 중...' : '인증코드 받기' }}
          </button>
          <button type="button" @click="backToLogin" class="mt-3 w-full text-center text-[13px] font-semibold text-muted-2 hover:text-ink transition-colors">
            로그인 화면으로 돌아가기
          </button>
        </section>

        <!-- ============ 잠금 해제 — 인증코드 입력 ============ -->
        <section v-else-if="step === 'unlock-code'">
          <p class="text-[18px] font-bold text-ink">이메일로 받은 인증코드를 입력해주세요</p>
          <p class="text-[13px] text-muted-2 mt-1.5">{{ email }}로 6자리 코드를 보냈어요</p>

          <div class="mt-6">
            <label class="text-[13px] font-semibold text-ink">인증코드</label>
            <input :value="unlockCode" @input="onUnlockCodeInput" type="text" inputmode="numeric" maxlength="6"
              placeholder="6자리 숫자 입력"
              class="mt-1.5 w-full rounded-xl border border-hairline px-3.5 py-3 text-[14px] tracking-[0.3em] outline-none focus:border-primary" />
            <p v-if="unlockCodeError" class="text-[12px] font-semibold text-red-500 mt-1.5">인증코드가 일치하지 않아요</p>
          </div>

          <button type="button" :disabled="resendCooldown > 0" @click="resendUnlockCode" class="mt-3 text-[12px] font-semibold text-primary disabled:text-muted-2 hover:underline transition-colors">
            {{ resendCooldown > 0 ? `${resendCooldown}초 후 다시 시도할 수 있어요` : '코드를 받지 못하셨나요? 재발송' }}
          </button>

          <button type="button" :disabled="!canSubmitUnlockCode" @click="submitUnlockCode"
            :class="canSubmitUnlockCode ? 'bg-primary hover:bg-primary/90' : 'bg-navborder'"
            class="mt-6 w-full rounded-xl text-white text-[15px] font-bold py-3.5 transition-colors">
            확인
          </button>
        </section>

        <!-- ============ 잠금 해제 완료 ============ -->
        <section v-else class="pt-4 flex flex-col items-center text-center">
          <div class="w-16 h-16 rounded-full bg-promo flex items-center justify-center">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#FF6000" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
          </div>
          <p class="text-[18px] font-bold text-ink mt-5">잠금이 해제됐어요</p>
          <p class="text-[13px] text-muted-2 mt-1.5">다시 로그인해주세요</p>
          <button type="button" @click="backToLogin" class="mt-8 w-full rounded-xl bg-primary text-white text-[15px] font-bold py-3.5 hover:bg-primary/90 transition-colors">
            로그인하러 가기
          </button>
        </section>
      </div>
    </div>
  </div>
</template>
