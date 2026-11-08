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

/**
 * Loads the series and the stats for one currency and range and shows them.
 *
 * Both requests run in parallel inside ONE loader (Promise.all), so they finish together: the chart and
 * the stats cards always come from the same request and can never show two different currencies.
 */
export function Dashboard({ code, range }: Props) {
  const from = range?.from
  const to = range?.to

  // A new loader whenever code/from/to change; useApi then aborts the old requests and starts new ones.
  const loadDashboard = useCallback(async (signal: AbortSignal) => {
    const [series, stats] = await Promise.all([
      fetchRates(code, { from, to }, signal),
      fetchStats(code, { from, to }, signal),
    ])
    return { series, stats }
  }, [code, from, to])
  const { data, error, loading } = useApi(range ? loadDashboard : null)

  if (error) {
    return (
      <p className="error" role="alert">
        {error.message}
      </p>
    )
  }
  if (!data) {
    return loading ? <p className="muted">Loading rates…</p> : null
  }

  return (
    // While a new range loads, the previous chart stays visible but dimmed.
    <section className={loading ? 'dashboard loading' : 'dashboard'} aria-busy={loading}>
      {data.series.rates.length === 0 ? (
        <p className="muted">
          No rates for {data.series.code} in this range (NBP publishes none on weekends and holidays). If the
          database is empty, run the ingestion first: <code>POST /api/admin/ingest</code>.
        </p>
      ) : (
        <>
          <StatsCards stats={data.stats} />
          <RateChart code={data.series.code} rates={data.series.rates} />
        </>
      )}
    </section>
  )
}
