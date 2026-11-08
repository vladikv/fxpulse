import { useCallback } from 'react'
import { fetchRates, fetchStats } from '../api/client'
import { useApi } from '../hooks/useApi'
import type { Range } from '../utils/ranges'
import { RateChart } from './RateChart'
import { StatsCards } from './StatsCards'

interface Props {
  code: string
  /** null while the picked range is invalid: nothing is requested then. */
  range: Range | null
}

/** Loads the series and the stats for one currency and range (two requests in parallel) and shows them. */
export function Dashboard({ code, range }: Props) {
  const from = range?.from
  const to = range?.to

  // A new loader whenever code/from/to change; useApi then aborts the old request and starts a new one.
  const loadRates = useCallback(
    (signal: AbortSignal) => fetchRates(code, { from, to }, signal),
    [code, from, to],
  )
  const loadStats = useCallback(
    (signal: AbortSignal) => fetchStats(code, { from, to }, signal),
    [code, from, to],
  )
  const series = useApi(range ? loadRates : null)
  const stats = useApi(range ? loadStats : null)

  const error = series.error ?? stats.error
  if (error) {
    return <p className="error">{error.message}</p>
  }
  if (!series.data || !stats.data) {
    return series.loading || stats.loading ? <p className="muted">Loading rates…</p> : null
  }

  const loading = series.loading || stats.loading
  return (
    // While a new range loads, the previous chart stays visible but dimmed.
    <section className={loading ? 'dashboard loading' : 'dashboard'} aria-busy={loading}>
      {series.data.rates.length === 0 ? (
        <p className="muted">
          No rates for {code} in this range (NBP publishes none on weekends and holidays). If the database is
          empty, run the ingestion first: <code>POST /api/admin/ingest</code>.
        </p>
      ) : (
        <>
          <StatsCards stats={stats.data} />
          <RateChart code={series.data.code} rates={series.data.rates} />
        </>
      )}
    </section>
  )
}
