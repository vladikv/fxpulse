// Types for the VITE_* variables we read via import.meta.env (Vite inlines them at build time).
interface ImportMetaEnv {
  /** Backend origin, e.g. http://localhost:8098 (no trailing slash needed). */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
