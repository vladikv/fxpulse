// Date helpers working on ISO date strings ("2026-11-08"), the format the backend uses for LocalDate.
// Calculations run in UTC so that daylight-saving changes can never shift a date by one day.

const DAY_MS = 24 * 60 * 60 * 1000

/** Today in the user's local time zone, as an ISO date. */
export function todayIso(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function parseUtc(iso: string): Date {
  return new Date(`${iso}T00:00:00Z`)
}

function formatUtc(date: Date): string {
  return date.toISOString().slice(0, 10)
}

export function minusDays(iso: string, days: number): string {
  return formatUtc(new Date(parseUtc(iso).getTime() - days * DAY_MS))
}

/** Same day N months earlier; JS rolls over invalid days (31 Mar - 1 month = 3 Mar), close enough here. */
export function minusMonths(iso: string, months: number): string {
  const date = parseUtc(iso)
  date.setUTCMonth(date.getUTCMonth() - months)
  return formatUtc(date)
}

/** Number of days in [from, to], both ends counted (the backend's max-range-days rule counts the same way). */
export function daysInclusive(from: string, to: string): number {
  return Math.round((parseUtc(to).getTime() - parseUtc(from).getTime()) / DAY_MS) + 1
}
