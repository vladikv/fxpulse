import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { daysInclusive } from './dates'
import { PRESETS } from './ranges'

function rangeOf(label: string) {
  const preset = PRESETS.find((p) => p.label === label)
  if (!preset) {
    throw new Error(`No preset ${label}`)
  }
  return preset.range()
}

describe('PRESETS', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 10, 8, 12, 0)) // 8 Nov 2026, noon local time
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('has the four quick ranges in display order', () => {
    expect(PRESETS.map((p) => p.label)).toEqual(['1M', '3M', '6M', '1Y'])
  })

  it('every range ends today', () => {
    for (const preset of PRESETS) {
      expect(preset.range().to).toBe('2026-11-08')
    }
  })

  it('month presets go back by calendar months', () => {
    expect(rangeOf('1M').from).toBe('2026-10-08')
    expect(rangeOf('3M').from).toBe('2026-08-08')
    expect(rangeOf('6M').from).toBe('2026-05-08')
  })

  it('1Y is exactly the backend limit of 366 days, counted inclusively', () => {
    const range = rangeOf('1Y')

    expect(range.from).toBe('2025-11-08')
    expect(daysInclusive(range.from, range.to)).toBe(366)
  })

  it('computes the range when called, not when the module was loaded', () => {
    vi.setSystemTime(new Date(2027, 0, 15, 12, 0))

    expect(rangeOf('1M')).toEqual({ from: '2026-12-15', to: '2027-01-15' })
  })
})
