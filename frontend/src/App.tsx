import { fetchCurrencies } from './api/client'
import { useApi } from './hooks/useApi'
import './App.css'

/**
 * Root component. For now it only proves the connection to the backend;
 * the dashboard, converter and AI insight panel are added in the next steps.
 */
function App() {
  // fetchCurrencies is a module function, so it is the same loader on every render: loads once.
  const currencies = useApi(fetchCurrencies)

  return (
    <main className="app">
      <header className="app-header">
        <h1>FxPulse</h1>
        <p className="subtitle">Official NBP exchange rates against PLN</p>
      </header>

      {currencies.loading && <p>Connecting to the backend…</p>}
      {currencies.error && <p className="error">{currencies.error.message}</p>}
      {currencies.data && <p>Backend connected: {currencies.data.length} currencies available.</p>}
    </main>
  )
}

export default App
