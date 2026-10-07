import { ref, onUnmounted } from 'vue'

export function useResendCooldown(seconds: number) {
  const cooldown = ref(0)
  let timer: ReturnType<typeof setInterval> | undefined

  function start() {
    if (timer) clearInterval(timer)
    cooldown.value = seconds
    timer = setInterval(() => {
      cooldown.value -= 1
      if (cooldown.value <= 0 && timer) clearInterval(timer)
    }, 1000)
  }

  onUnmounted(() => timer && clearInterval(timer))

  return { cooldown, start }
}
