// TypeScript mirrors of the backend's response records (com.vlad.fxpulse.*).
// Dates are ISO strings ("2026-11-06"), Instants are ISO timestamps.
// Java BigDecimal is serialized by Jackson as a JSON number, so it arrives as a JS `number`.
// That is fine here: the UI only displays values; every calculation happens in the backend with BigDecimal.

/** currency/CurrencyDto */
export interface Currency {
  code: string
  name: string
}

/** rate/RatePoint */
export interface RatePoint {
  date: string
  mid: number
}

/** rate/RateSeriesResponse */
export interface RateSeries {
  code: string
  from: string
  to: string
  rates: RatePoint[]
}

/** rate/RateStatsResponse. The numbers are null when the range has no rates (count = 0). */
export interface RateStats {
  code: string
  from: string
  to: string
  count: number
  min: number | null
  max: number | null
  avg: number | null
  first: number | null
  last: number | null
  changePercent: number | null
  volatility: number | null
}

/** rate/ConversionResponse. The rate dates are null for PLN (it has no NBP rate). */
export interface Conversion {
  from: string
  to: string
  amount: number
  result: number
  rate: number
  fromRateDate: string | null
  toRateDate: string | null
}

/** insight/InsightResponse */
export interface Insight {
  code: string
  from: string
  to: string
  content: string
  model: string
  createdAt: string
  /** true when the text came from the database instead of a fresh AI call */
  cached: boolean
}

/** common/ApiError: the body of every error response. */
export interface ApiError {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}
