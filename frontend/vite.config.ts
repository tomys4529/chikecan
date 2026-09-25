import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    // 日時のローカルタイムゾーン変換テストが実行環境依存で不安定にならないよう、
    // テスト実行時のタイムゾーンをAsia/Tokyo(UTC+9)へ固定する。
    env: {
      TZ: 'Asia/Tokyo',
    },
  },
})
