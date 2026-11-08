import type { RateStats } from '../api/types'
import { formatPercent, formatRate } from '../utils/format'

interface Props {
  stats: RateStats
}

/** Summary numbers for the selected range, all computed by the backend (StatsCalculator). */
export function StatsCards({ stats }: Props) {
  const change = stats.changePercent
  // The arrow repeats what the colour says, so the direction is never shown by colour alone.
  const trend = change === null || change === 0 ? 'flat' : change > 0 ? 'up' : 'down'
  const arrow = { up: '▲', down: '▼', flat: '' }[trend]

  return (
    <dl className="stats">
      <Card label="Change" valueClass={`trend-${trend}`}>
        {arrow} {formatPercent(change)}
      </Card>
      <Card label="Last">{formatRate(stats.last)}</Card>
      <Card label="First">{formatRate(stats.first)}</Card>
      <Card label="Average">{formatRate(stats.avg)}</Card>
      <Card label="Min">{formatRate(stats.min)}</Card>
      <Card label="Max">{formatRate(stats.max)}</Card>
      <Card label="Volatility (std dev)">{formatRate(stats.volatility)}</Card>
      <Card label="Days with rates">{stats.count}</Card>
    </dl>
  )
}

interface CardProps {
  label: string
  valueClass?: string
  children: React.ReactNode
}

function Card({ label, valueClass, children }: CardProps) {
  return (
    <div className="stat-card">
      <dt>{label}</dt>
      <dd className={valueClass}>{children}</dd>
    </div>
  )
}
