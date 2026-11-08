import type { Currency } from '../api/types'

interface Props {
  currencies: Currency[]
  value: string
  onChange: (code: string) => void
}

/** Drop-down of all ingested currencies; NBP names are Polish, e.g. "EUR – euro". */
export function CurrencySelector({ currencies, value, onChange }: Props) {
  return (
    <label className="field">
      <span className="field-label">Currency</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {currencies.map((currency) => (
          <option key={currency.code} value={currency.code}>
            {currency.code} – {currency.name}
          </option>
        ))}
      </select>
    </label>
  )
}
