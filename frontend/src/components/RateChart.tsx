import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { RatePoint } from '../api/types'
import { formatDate, formatRate, formatShortDate } from '../utils/format'

interface Props {
  code: string
  rates: RatePoint[]
}

/**
 * Line chart of the PLN mid rate over time. A single series, so no legend: the heading names it.
 * Colours come from CSS variables, so the chart follows the light/dark theme.
 */
export function RateChart({ code, rates }: Props) {
  return (
    <figure className="chart">
      <figcaption className="chart-title">{code} / PLN – NBP mid rate</figcaption>
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={rates} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--grid)" />
          <XAxis
            dataKey="date"
            tickFormatter={formatShortDate}
            minTickGap={32}
            tick={{ fill: 'var(--muted)', fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: 'var(--grid)' }}
          />
          <YAxis
            // 'auto' fits the axis to the data instead of starting at 0, so small moves stay visible.
            domain={['auto', 'auto']}
            tickFormatter={(value: number) => formatRate(value)}
            width={64}
            tick={{ fill: 'var(--muted)', fontSize: 12 }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            labelFormatter={(label) => formatDate(String(label))}
            formatter={(value) => [`${formatRate(Number(value))} PLN`, `1 ${code}`]}
            contentStyle={{
              background: 'var(--surface)',
              border: '1px solid var(--border)',
              borderRadius: 8,
              color: 'var(--text)',
            }}
            cursor={{ stroke: 'var(--muted)', strokeWidth: 1 }}
          />
          <Line
            type="monotone"
            dataKey="mid"
            stroke="var(--chart-line)"
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 5, fill: 'var(--chart-line)', stroke: 'var(--surface)', strokeWidth: 2 }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>

      {/* The same data as text, for screen readers and anyone who wants exact values. */}
      <details className="data-table">
        <summary>Show data table ({rates.length} days)</summary>
        <table>
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">Mid rate (PLN)</th>
            </tr>
          </thead>
          <tbody>
            {rates.map((point) => (
              <tr key={point.date}>
                <td>{formatDate(point.date)}</td>
                <td className="num">{formatRate(point.mid)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  )
}
