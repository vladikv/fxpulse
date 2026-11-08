import { describe, expect, it } from 'vitest'
import {
  formatAmount,
  formatCrossRate,
  formatDate,
  formatInputAmount,
  formatPercent,
  formatRate,
  formatShortDate,
} from './format'

describe('format', () => {
  it('formatRate shows 4 decimals, a dash for null', () => {
    expect(formatRate(4.2)).toBe('4.2000')
    expect(formatRate(4.23456)).toBe('4.2346')
    expect(formatRate(null)).toBe('–')
  })

  it('formatPercent shows a sign except for zero, a dash for null', () => {
    expect(formatPercent(1.234)).toBe('+1.23%')
    expect(formatPercent(-0.5)).toBe('-0.50%')
    expect(formatPercent(0)).toBe('0.00%')
    expect(formatPercent(null)).toBe('–')
  })

  it('date formats read the ISO date as UTC, so the time zone cannot shift the day', () => {
    expect(formatShortDate('2026-11-06')).toBe('6 Nov')
    expect(formatDate('2026-11-06')).toBe('6 Nov 2026')
    expect(formatDate('2026-01-01')).toBe('1 Jan 2026')
  })

  it('formatAmount uses 2 decimals and thousands separators', () => {
    expect(formatAmount(1234.5)).toBe('1,234.50')
    expect(formatAmount(111.714)).toBe('111.71')
  })

  it('formatInputAmount keeps up to 6 decimals but shows at least 2', () => {
    expect(formatInputAmount(100)).toBe('100.00')
    expect(formatInputAmount(0.123456)).toBe('0.123456')
  })

  it('formatCrossRate keeps 6 significant digits, so tiny rates stay readable', () => {
    expect(formatCrossRate(0.00004761234)).toBe('0.0000476123')
    expect(formatCrossRate(1.11712345)).toBe('1.11712')
  })
})
