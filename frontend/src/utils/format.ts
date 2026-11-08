// Display formatting only - the values themselves were calculated by the backend.

const rateFormat = new Intl.NumberFormat('en-US', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
const percentFormat = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
})
const shortDateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' })
const longDateFormat = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

/** NBP mid rates have 4 decimal places. */
export function formatRate(value: number | null): string {
  return value === null ? '–' : rateFormat.format(value)
}

export function formatPercent(value: number | null): string {
  return value === null ? '–' : `${percentFormat.format(value)}%`
}

/** "6 Nov" - for chart axis ticks. */
export function formatShortDate(iso: string): string {
  return shortDateFormat.format(new Date(`${iso}T00:00:00Z`))
}

/** "6 Nov 2026" */
export function formatDate(iso: string): string {
  return longDateFormat.format(new Date(`${iso}T00:00:00Z`))
}

const amountFormat = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
// The backend accepts up to 6 decimals in the amount, so show them instead of rounding the user's input.
const inputAmountFormat = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 })
const crossRateFormat = new Intl.NumberFormat('en-US', { maximumSignificantDigits: 6 })

/** Money amounts: 2 decimals with thousands separators, e.g. "1,234.50" (the backend's result scale). */
export function formatAmount(value: number): string {
  return amountFormat.format(value)
}

/** The amount the user entered, e.g. "0.123456" stays as is (but "100" shows as "100.00"). */
export function formatInputAmount(value: number): string {
  return inputAmountFormat.format(value)
}

/**
 * Cross rates by significant digits, not decimals: 1 IDR = 0.0000476 EUR would show as 0.00 with 2 decimals.
 * (The backend sends 10 significant digits; 6 are plenty to read.)
 */
export function formatCrossRate(value: number): string {
  return crossRateFormat.format(value)
}
