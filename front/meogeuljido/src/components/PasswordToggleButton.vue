<!-- src/components/PasswordToggleButton.vue -->
<script setup lang="ts">
defineProps<{ visible: boolean }>()
defineEmits<{ toggle: [] }>()
</script>

<template>
  <button type="button" aria-label="비밀번호 표시" @click="$emit('toggle')"
    class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-2 hover:text-ink transition-colors">
    <svg v-if="!visible" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/>
      <circle cx="12" cy="12" r="3"/>
    </svg>
    <svg v-else width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a18.5 18.5 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
      <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/>
      <line x1="1" y1="1" x2="23" y2="23"/>
    </svg>
  </button>
</template>
