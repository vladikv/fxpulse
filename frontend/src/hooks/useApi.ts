import { useEffect, useState } from 'react'

/** A request function; it must stop when the signal is aborted (fetch does this for us). */
export type Loader<T> = (signal: AbortSignal) => Promise<T>

export interface ApiState<T> {
  data: T | undefined
  error: Error | undefined
  loading: boolean
}

/** The outcome of one finished request, remembered together with the loader that produced it. */
interface Result<T> {
  source: Loader<T>
  data?: T
  error?: Error
}

/**
 * Runs `load` and tracks its data / error / loading state. Pass `null` to load nothing.
 *
 * The loader must be stable between renders: a module function (e.g. `fetchCurrencies`) or a
 * `useCallback` listing what it reads, e.g. `useCallback(s => fetchRates(code, range, s), [code, range])`.
 * A new loader means "the inputs changed, load again".
 *
 * - Each run gets its own AbortController. When the loader changes again (the user picks another
 *   currency before the previous response arrived) or the component unmounts, the old request is
 *   aborted, so a slow, outdated response can never overwrite a newer one.
 * - `loading` is derived, not stored: we are loading while the last result belongs to an older loader.
 *   That avoids calling setState synchronously inside the effect (an extra render each time).
 * - The previous data stays available while the next request runs, so a chart doesn't flash empty.
 */
export function useApi<T>(load: Loader<T> | null): ApiState<T> {
  const [result, setResult] = useState<Result<T> | null>(null)

  useEffect(() => {
    if (load === null) {
      return
    }
    const controller = new AbortController()
    load(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setResult({ source: load, data })
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ source: load, error: error instanceof Error ? error : new Error(String(error)) })
        }
      })
    return () => controller.abort()
  }, [load])

  if (load === null) {
    return { data: undefined, error: undefined, loading: false }
  }
  const isCurrent = result?.source === load
  return {
    data: result?.data,
    error: isCurrent ? result?.error : undefined,
    loading: !isCurrent,
  }
}
