import { ref } from 'vue'

/**
 * api/client.ts(axios 인터셉터)와 stores/auth.ts(Pinia) 양쪽이 accessToken을 참조해야 하는데,
 * 스토어가 api/auth.ts를 호출하고 api/auth.ts는 client.ts를 쓰므로
 * client.ts가 stores/auth.ts를 직접 import하면 순환 참조가 생김
 * accessToken 자체를 이 얕은 모듈로 분리해 순환을 끊는다.
 */
export const accessToken = ref<string | null >(null)

export function setAccessToken(token: string | null) {
  accessToken.value = token
}
