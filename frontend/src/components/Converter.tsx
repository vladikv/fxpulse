import { useEffect, useRef, useState, type FormEvent } from 'react'
import { convert } from '../api/client'
import type { Conversion, Currency } from '../api/types'
import { todayIso } from '../utils/dates'
import { formatAmount, formatCrossRate, formatDate, formatInputAmount } from '../utils/format'

interface Props {
  currencies: Currency[]
}

/** PLN is not in NBP table A (every rate is quoted against it), but the backend accepts it for conversion. */
const PLN = 'PLN'

/** Same limits as the backend's @Positive @Digits(integer = 15, fraction = 6) on "amount". */
const AMOUNT_PATTERN = /^\d{1,15}(\.\d{1,6})?$/

/** Accepts "1 234,5" as well as "1234.5"; returns null if it is not a valid positive amount. */
function normalizeAmount(input: string): string | null {
  const amount = input.trim().replace(/\s/g, '').replace(',', '.')
  return AMOUNT_PATTERN.test(amount) && Number(amount) > 0 ? amount : null
}

/**
 * Converts an amount between any two currencies (cross rate via PLN, calculated by the backend).
 * Runs on submit, not on every keystroke, so typing "1000" doesn't fire four requests.
 */
export function Converter({ currencies }: Props) {
  const [amount, setAmount] = useState('100')
  const [from, setFrom] = useState('EUR')
  const [to, setTo] = useState(PLN)
  const [date, setDate] = useState('')

  // The date that was submitted is kept with the answer, so editing the date input later can't change the note.
  const [result, setResult] = useState<{ conversion: Conversion; date: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  // The request in flight, so a new submit (or leaving the page) can cancel it.
  const requestRef = useRef<AbortController | null>(null)

  useEffect(() => () => requestRef.current?.abort(), [])

  const codes = [PLN, ...currencies.map((c) => c.code)]

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalized = normalizeAmount(amount)
    if (normalized === null) {
      setError('Enter a positive amount with at most 6 decimal places.')
      return
    }

    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    setLoading(true)
    setError(null)
    try {
      const response = await convert({ from, to, amount: normalized, date: date || undefined }, controller.signal)
      setResult({ conversion: response, date })
    } catch (err) {
      if (controller.signal.aborted) {
        return // replaced by a newer request; that one updates the state
      }
      setResult(null)
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false)
      }
    }
  }

  function swap() {
    setFrom(to)
    setTo(from)
  }

  return (
    <section className="panel">
      <h2 className="panel-title">Converter</h2>
      <form className="controls" onSubmit={handleSubmit}>
        <label className="field">
          <span className="field-label">Amount</span>
          <input
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            className="amount-input"
          />
        </label>
        <CodeSelect label="From" value={from} codes={codes} onChange={setFrom} />
        <button type="button" onClick={swap} aria-label="Swap currencies" title="Swap currencies">
          ⇄
        </button>
        <CodeSelect label="To" value={to} codes={codes} onChange={setTo} />
        <label className="field">
          <span className="field-label">Date (optional)</span>
          <input type="date" value={date} max={todayIso()} onChange={(event) => setDate(event.target.value)} />
        </label>
        <button type="submit" className="primary" disabled={loading}>
          {loading ? 'Converting…' : 'Convert'}
        </button>
      </form>

      {error && <p className="error">{error}</p>}
      {result && !error && <ConversionResult result={result.conversion} requestedDate={result.date} />}
    </section>
  )
}

interface CodeSelectProps {
  label: string
  value: string
  codes: string[]
  onChange: (code: string) => void
}

function CodeSelect({ label, value, codes, onChange }: CodeSelectProps) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>
        {codes.map((code) => (
          <option key={code} value={code}>
            {code}
          </option>
        ))}
      </select>
    </label>
  )
}

interface ResultProps {
  result: Conversion
  /** The date sent with the request; empty = today. */
  requestedDate: string
}

/** Shows the answer plus where it came from: the cross rate and the dates of the NBP rates used. */
function ConversionResult({ result, requestedDate }: ResultProps) {
  // PLN has no NBP rate, so its date is null; list only the real ones.
  const rateDates = [
    { code: result.from, date: result.fromRateDate },
    { code: result.to, date: result.toRateDate },
  ].filter((entry): entry is { code: string; date: string } => entry.date !== null)

  const targetDate = requestedDate || todayIso()
  const fellBack = rateDates.some((entry) => entry.date < targetDate)

  return (
    <div className="conversion-result" aria-live="polite">
      <p className="conversion-main">
        {formatInputAmount(result.amount)} {result.from} = <strong>{formatAmount(result.result)} {result.to}</strong>
      </p>
      <p className="muted">
        1 {result.from} = {formatCrossRate(result.rate)} {result.to}
        {rateDates.length > 0 && (
          <>
            {' · NBP rates of '}
            {rateDates.map((entry) => `${formatDate(entry.date)} (${entry.code})`).join(', ')}
          </>
        )}
      </p>
      {fellBack && (
        <p className="muted small">
          NBP publishes no rate for {formatDate(targetDate)} (weekend, holiday or not published yet), so the latest
          earlier rate was used.
        </p>
      )}
    </div>
  )
}
