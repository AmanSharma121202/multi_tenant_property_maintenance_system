import { useCallback, useRef } from 'react'

/** Ignore out-of-order async responses (e.g. React StrictMode double fetch). */
export function useLoadSequence() {
  const seq = useRef(0)

  const nextLoadId = useCallback(() => {
    seq.current += 1
    return seq.current
  }, [])

  const isLatest = useCallback((id: number) => id === seq.current, [])

  return { nextLoadId, isLatest }
}
