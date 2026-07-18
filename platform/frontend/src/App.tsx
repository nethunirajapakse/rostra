function App() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-900 px-4 text-center text-white">
      {/* Updated card with an actual emerald glow effect */}
      <div className="max-w-md rounded-2xl bg-slate-800 p-8 shadow-[0_0_50px_-12px_rgba(16,185,129,0.3)] ring-2 ring-emerald-500/30">
        <h1 className="text-3xl font-extrabold tracking-tight text-emerald-400 sm:text-4xl">
          Tailwind Test
        </h1>
        <p className="mt-4 text-slate-400">
          Now you should see a distinct, soft emerald glow radiating behind this card!
        </p>
        <button className="mt-6 rounded-xl bg-emerald-500 px-6 py-2.5 font-semibold text-slate-950 transition-all hover:bg-emerald-400 active:scale-95 shadow-[0_0_20px_rgba(16,185,129,0.4)]">
          Looks Good 🎉
        </button>
      </div>
    </div>
  )
}

export default App