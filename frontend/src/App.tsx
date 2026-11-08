import { useState } from 'react'
import { fetchCurrencies } from './api/client'
import { CurrencySelector } from './components/CurrencySelector'
import { Converter } from './components/Converter'
import { Dashboard } from './components/Dashboard'
import { DateRangePicker } from './components/DateRangePicker'
import { InsightPanel } from './components/InsightPanel'
import { useApi } from './hooks/useApi'
import { useDelayedFlag } from './hooks/useDelayedFlag'
import { daysInclusive } from './utils/dates'
import { PRESETS, type Range } from './utils/ranges'
import './App.css'

/** Same limit as the backend's app.rates.max-range-days; the backend still validates on its own. */
const MAX_RANGE_DAYS = 366
const DEFAULT_CODE = 'EUR'
/** After this long without an answer, explain the free hosting's cold start. */
const SLOW_START_MS = 5000

/** Returns a message when the range can't be requested, or null when it is fine. */
function validateRange({ from, to }: Range): string | null {
  if (!from || !to) {
    return 'Pick both dates.'
  }
  if (from > to) {
    // ISO dates compare correctly as strings.
    return '"From" must not be after "To".'
  }
  if (daysInclusive(from, to) > MAX_RANGE_DAYS) {
    return `The range may cover at most ${MAX_RANGE_DAYS} days.`
  }
  return null
}

/** Root component: owns the selected currency and range and passes them to the dashboard. */
function App() {
  // fetchCurrencies is a module function, so it is the same loader on every render: loads once.
  const currencies = useApi(fetchCurrencies)
  const slowStart = useDelayedFlag(currencies.loading, SLOW_START_MS)
  const [code, setCode] = useState(DEFAULT_CODE)
  const [range, setRange] = useState<Range>(() => PRESETS[0].range())

  const list = currencies.data ?? []
  // Derived instead of fixed in an effect: if EUR is not in the database, fall back to the first currency.
  const selectedCode = list.some((c) => c.code === code) ? code : list[0]?.code
  const rangeError = validateRange(range)
  // null while the range is invalid: the dashboard and the insight panel then request nothing.
  const validRange = rangeError ? null : range

  return (
    <main className="app">
      <header className="app-header">
        <h1>FxPulse</h1>
        <p className="subtitle">Official NBP exchange rates against PLN</p>
      </header>

      {currencies.loading && (
        <p className="muted" role="status">
          {slowStart
            ? 'The free server is waking up after a quiet period. This can take up to a minute…'
            : 'Connecting to the backend…'}
        </p>
      )}
      {currencies.error && (
        <p className="error" role="alert">
          {currencies.error.message}
        </p>
      )}
      {currencies.data && list.length === 0 && (
        <p className="muted">
          The database has no currencies yet. Run the ingestion first: <code>POST /api/admin/ingest</code>.
        </p>
      )}

      {selectedCode && (
        <>
          <section className="panel">
            <div className="controls">
              <CurrencySelector currencies={list} value={selectedCode} onChange={setCode} />
              <DateRangePicker value={range} onChange={setRange} />
            </div>
            {rangeError && (
              <p className="error" role="alert">
                {rangeError}
              </p>
            )}
            <Dashboard code={selectedCode} range={validRange} />
          </section>
          {/* Uses the dashboard's currency and range, so the summary always describes what the chart shows. */}
          <InsightPanel code={selectedCode} range={validRange} />
        </>
      )}

      {list.length > 0 && <Converter currencies={list} />}
    </main>
  )
}

export default App
