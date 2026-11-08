import type { ApiError, Conversion, Currency, Insight, RateSeries, RateStats } from './types'

/** Backend origin from .env (VITE_API_BASE_URL); falls back to the local backend port. */
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8098').replace(/\/+$/, '')

/**
 * A failed API call. `status` is the HTTP status (0 = the backend could not be reached at all), and
 * `message` is the backend's ApiError.message when there is one, so it can be shown to the user as is.
 */
export class ApiRequestError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
  }
}

/** Query parameters; undefined and empty values are left out of the URL (the backend then uses its defaults). */
type Params = Record<string, string | number | undefined>

async function getJson<T>(path: string, params: Params = {}, signal?: AbortSignal): Promise<T> {
  const url = new URL(API_BASE_URL + path)
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      url.searchParams.set(key, String(value))
    }
  }

  let response: Response
  try {
    response = await fetch(url, { signal, headers: { Accept: 'application/json' } })
  } catch (error) {
    // An aborted request is not an error to show; let the caller recognise it.
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    // fetch only rejects on network problems: backend down, wrong URL, or blocked by CORS.
    throw new ApiRequestError(0, `Cannot reach the backend at ${API_BASE_URL}`)
  }

  if (!response.ok) {
    throw new ApiRequestError(response.status, await errorMessage(response))
  }
  return (await response.json()) as T
}

/** Uses ApiError.message from the body; falls back to the status line if the body is not our JSON. */
async function errorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as Partial<ApiError>
    if (typeof body.message === 'string' && body.message !== '') {
      return body.message
    }
  } catch {
    // not JSON (e.g. a proxy error page) - use the fallback below
  }
  return `Request failed: ${response.status} ${response.statusText}`.trim()
}

// ---- Typed endpoint functions (one per backend endpoint) ----

/** Inclusive ISO date range; either end may be omitted to use the backend default. */
export interface DateRange {
  from?: string
  to?: string
}

export function fetchCurrencies(signal?: AbortSignal): Promise<Currency[]> {
  return getJson<Currency[]>('/api/currencies', {}, signal)
}

export function fetchRates(code: string, range: DateRange, signal?: AbortSignal): Promise<RateSeries> {
  return getJson<RateSeries>(`/api/rates/${encodeURIComponent(code)}`, { ...range }, signal)
}

export function fetchStats(code: string, range: DateRange, signal?: AbortSignal): Promise<RateStats> {
  return getJson<RateStats>(`/api/rates/${encodeURIComponent(code)}/stats`, { ...range }, signal)
}

export interface ConversionRequest {
  from: string
  to: string
  /** Sent as typed (string), so the backend parses it as BigDecimal without a float round trip. */
  amount: string
  date?: string
}

export function convert(request: ConversionRequest, signal?: AbortSignal): Promise<Conversion> {
  return getJson<Conversion>('/api/convert', { ...request }, signal)
}

export function fetchInsight(code: string, range: DateRange, signal?: AbortSignal): Promise<Insight> {
  return getJson<Insight>(`/api/insights/${encodeURIComponent(code)}`, { ...range }, signal)
}
