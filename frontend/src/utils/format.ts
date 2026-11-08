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
