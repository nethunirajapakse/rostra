import { useQuery } from '@tanstack/react-query'

interface Pokemon {
  name: string;
  sprites: { front_default: string };
}

function App() {
  // Simple fetch test with React Query
  const { data, isLoading, error } = useQuery<Pokemon>({
    queryKey: ['pokemon'],
    queryFn: async () => {
      const res = await fetch('https://pokeapi.co/api/v2/pokemon/ditto')
      if (!res.ok) throw new Error('Network response was not ok')
      return res.json()
    },
  })

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-900 text-white p-4">
      <div className="rounded-2xl bg-slate-800 p-6 shadow-xl border border-slate-700 text-center max-w-sm w-full">
        
        <h1 className="text-2xl font-bold tracking-tight text-indigo-400 mb-2">
          Rostra Frontend Setup
        </h1>
        
        <div className="mt-4 p-4 rounded-lg bg-slate-950/50 border border-slate-800">
          <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-400">
            React Query Status:
          </h2>
          
          {isLoading && <p className="text-yellow-400 mt-2 animate-pulse">Fetching data...</p>}
          
          {error && <p className="text-red-400 mt-2">Error: {error.message}</p>}
          
          {data && (
            <div className="mt-2 flex flex-col items-center">
              <p className="text-green-400 font-medium">✓ Successfully Connected!</p>
              <img 
                src={data.sprites.front_default} 
                alt={data.name} 
                className="w-24 h-24 mt-2 animate-bounce"
              />
              <p className="capitalize font-mono text-sm bg-slate-900 px-3 py-1 rounded border border-slate-800 mt-1">
                {data.name} fetched via cache
              </p>
            </div>
          )}
        </div>
        
        <p className="mt-4 text-xs text-slate-500">
          If the background is dark gray and the title is purple, Tailwind is working.
        </p>
      </div>
    </div>
  )
}

export default App