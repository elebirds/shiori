export interface PopupLifecycleController {
  schedule: (id: string, onExpire: () => void) => void
  clear: (id: string) => void
  clearAll: () => void
  pruneByAliveIds: (aliveIds: Set<string>) => void
}

export function createPopupLifecycle(autoDismissMs: number): PopupLifecycleController {
  const timers = new Map<string, ReturnType<typeof setTimeout>>()

  function clear(id: string): void {
    const timer = timers.get(id)
    if (!timer) {
      return
    }
    clearTimeout(timer)
    timers.delete(id)
  }

  function schedule(id: string, onExpire: () => void): void {
    clear(id)
    timers.set(
      id,
      setTimeout(() => {
        timers.delete(id)
        onExpire()
      }, autoDismissMs),
    )
  }

  function clearAll(): void {
    for (const timer of timers.values()) {
      clearTimeout(timer)
    }
    timers.clear()
  }

  function pruneByAliveIds(aliveIds: Set<string>): void {
    for (const id of timers.keys()) {
      if (!aliveIds.has(id)) {
        clear(id)
      }
    }
  }

  return {
    schedule,
    clear,
    clearAll,
    pruneByAliveIds,
  }
}
