import { useEffect, useState } from 'react'

/**
 * Becomes true only when `active` has stayed true for `delayMs`; false again as soon as it ends.
 * Used to show "the server is waking up" only for slow loads, not flash it on every fast one.
 *
 * The setState calls run in a timer callback (or the cleanup), never synchronously in the effect body.
 */
export function useDelayedFlag(active: boolean, delayMs: number): boolean {
  const [elapsed, setElapsed] = useState(false)

  useEffect(() => {
    if (!active) {
      return
    }
    const timer = setTimeout(() => setElapsed(true), delayMs)
    return () => {
      clearTimeout(timer)
      setElapsed(false)
    }
  }, [active, delayMs])

  return active && elapsed
}
