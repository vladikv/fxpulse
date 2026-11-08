import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDelayedFlag } from './useDelayedFlag'

describe('useDelayedFlag', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('stays false while active for less than the delay', () => {
    const { result } = renderHook(() => useDelayedFlag(true, 5000))

    act(() => vi.advanceTimersByTime(4999))

    expect(result.current).toBe(false)
  })

  it('becomes true once active for the whole delay', () => {
    const { result } = renderHook(() => useDelayedFlag(true, 5000))

    act(() => vi.advanceTimersByTime(5000))

    expect(result.current).toBe(true)
  })

  it('never becomes true while inactive', () => {
    const { result } = renderHook(() => useDelayedFlag(false, 5000))

    act(() => vi.advanceTimersByTime(60_000))

    expect(result.current).toBe(false)
  })

  it('turns false in the very render in which active turns false', () => {
    // result.current only shows the value after effects ran (the cleanup resets the state anyway),
    // so record every render to catch a single stale "true" right after loading ends.
    const values: boolean[] = []
    const { rerender } = renderHook(
      ({ active }) => {
        const value = useDelayedFlag(active, 5000)
        values.push(value)
        return value
      },
      { initialProps: { active: true } },
    )
    act(() => vi.advanceTimersByTime(5000))
    expect(values.at(-1)).toBe(true)
    const rendersBefore = values.length

    rerender({ active: false })

    expect(values.slice(rendersBefore)).not.toContain(true)
  })

  it('starts the delay again when re-activated', () => {
    const { result, rerender } = renderHook(({ active }) => useDelayedFlag(active, 5000), {
      initialProps: { active: true },
    })
    act(() => vi.advanceTimersByTime(5000))
    rerender({ active: false })

    rerender({ active: true })
    act(() => vi.advanceTimersByTime(4999))
    expect(result.current).toBe(false)

    act(() => vi.advanceTimersByTime(1))
    expect(result.current).toBe(true)
  })

  it('cancels the pending timer when deactivated before the delay', () => {
    const { result, rerender } = renderHook(({ active }) => useDelayedFlag(active, 5000), {
      initialProps: { active: true },
    })
    act(() => vi.advanceTimersByTime(3000))

    rerender({ active: false })

    expect(vi.getTimerCount()).toBe(0)
    act(() => vi.advanceTimersByTime(10_000))
    expect(result.current).toBe(false)
  })
})
