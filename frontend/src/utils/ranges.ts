import { minusDays, minusMonths, todayIso } from './dates'

/** Inclusive range of ISO dates; both ends are always set in the UI. */
export interface Range {
  from: string
  to: string
}

export interface Preset {
  label: string
  range: () => Range
}

/** Quick ranges ending today. 1Y = 366 days counted inclusively, exactly the backend's max-range-days. */
export const PRESETS: Preset[] = [
  { label: '1M', range: () => ({ from: minusMonths(todayIso(), 1), to: todayIso() }) },
  { label: '3M', range: () => ({ from: minusMonths(todayIso(), 3), to: todayIso() }) },
  { label: '6M', range: () => ({ from: minusMonths(todayIso(), 6), to: todayIso() }) },
  { label: '1Y', range: () => ({ from: minusDays(todayIso(), 365), to: todayIso() }) },
]
