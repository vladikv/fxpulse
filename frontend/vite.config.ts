/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Vitest reads this same file. jsdom gives the hook tests a browser-like DOM for React to render into.
  test: {
    environment: 'jsdom',
  },
})
