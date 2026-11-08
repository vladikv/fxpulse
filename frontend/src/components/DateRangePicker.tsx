import { todayIso } from '../utils/dates'
import { PRESETS, type Range } from '../utils/ranges'

interface Props {
  value: Range
  onChange: (range: Range) => void
}

/** Preset buttons plus two native date inputs (no date-picker library needed). */
export function DateRangePicker({ value, onChange }: Props) {
  const today = todayIso()

  return (
    <div className="date-range">
      <div className="presets" role="group" aria-label="Quick ranges">
        {PRESETS.map((preset) => {
          const range = preset.range()
          const active = range.from === value.from && range.to === value.to
          return (
            <button
              key={preset.label}
              type="button"
              className={active ? 'preset active' : 'preset'}
              aria-pressed={active}
              onClick={() => onChange(range)}
            >
              {preset.label}
            </button>
          )
        })}
      </div>
      <label className="field">
        <span className="field-label">From</span>
        <input
          type="date"
          value={value.from}
          max={value.to || today}
          onChange={(event) => onChange({ ...value, from: event.target.value })}
        />
      </label>
      <label className="field">
        <span className="field-label">To</span>
        <input
          type="date"
          value={value.to}
          min={value.from}
          max={today}
          onChange={(event) => onChange({ ...value, to: event.target.value })}
        />
      </label>
    </div>
  )
}
