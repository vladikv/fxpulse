import { afterEach, describe, expect, it, vi } from 'vitest'
import { daysInclusive, minusDays, minusMonths, todayIso } from './dates'

describe('dates', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('todayIso formats the local date with zero padding', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 0, 5, 12, 0)) // 5 Jan 2026, noon local time

    expect(todayIso()).toBe('2026-01-05')
  })

  it('minusDays crosses month and year boundaries', () => {
    expect(minusDays('2026-03-01', 1)).toBe('2026-02-28')
    expect(minusDays('2026-01-01', 1)).toBe('2025-12-31')
    expect(minusDays('2024-03-01', 1)).toBe('2024-02-29') // leap year
  })

  it('minusDays is not shifted by a daylight-saving change', () => {
    // 29 Mar 2026 is the DST switch in Europe; the UTC calculation ignores it.
    expect(minusDays('2026-03-30', 1)).toBe('2026-03-29')
    expect(minusDays('2026-03-30', 2)).toBe('2026-03-28')
  })

  it('minusMonths keeps the day of the month', () => {
    expect(minusMonths('2026-11-08', 1)).toBe('2026-10-08')
    expect(minusMonths('2026-02-15', 3)).toBe('2025-11-15')
  })

  it('minusMonths rolls over a day that the target month does not have', () => {
    expect(minusMonths('2026-03-31', 1)).toBe('2026-03-03')
  })

  it('daysInclusive counts both ends', () => {
    expect(daysInclusive('2026-11-08', '2026-11-08')).toBe(1)
    expect(daysInclusive('2026-11-01', '2026-11-08')).toBe(8)
    expect(daysInclusive('2025-11-08', '2026-11-08')).toBe(366)
  })
})
