# HealthSync AI

An offline-first, multi-device health intelligence platform built to production standards.

## Architecture

- **Offline-first**: All health events stored locally in Room DB with WorkManager sync queue
- **CRDT Sync**: Max-Register conflict resolution across Watch, Phone, and Ring
- **Clean Architecture**: Domain / Data / UI layers with strict separation
- **Dependency Injection**: Full Hilt DI graph — testable, scalable
- **Reactive UI**: StateFlow + Jetpack Compose with lifecycle-aware collection

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material3 |
| Architecture | MVVM, Clean Architecture |
| DI | Hilt |
| Local DB | Room + SQLite |
| Background Sync | WorkManager |
| Networking | Retrofit + OkHttp |
| Async | Kotlin Coroutines + Flow |
| Backend | FastAPI + PostgreSQL (Day 2) |
| AI Agent | Claude API + Tool Use (Day 4) |

## The Hard Problems

### Multi-device Conflict Resolution
When the same user walks on both Phone and Watch while offline,
both devices record steps independently. On sync, a Max-Register
CRDT merge resolves conflicts — the highest recorded value wins,
guaranteeing convergence with zero data loss.
```
Phone: 5,000 steps  ──→  max(5000, 8000) = 8,000 ✅
Watch: 8,000 steps  ──→
```

### Offline-first Architecture
Every health event is written to Room DB first with `isSynced = false`.
WorkManager monitors network availability and syncs in batches
with exponential backoff — no data loss even on flaky connections.

### Scalable Device Model
Devices are not hardcoded. The UI derives a `Map<DeviceId, Steps>`
from Room, rendering one card per device dynamically. Adding a new
device requires zero code changes.

## Project Structure
```
app/
├── data/
│   ├── local/          Room DB, DAOs, entities
│   ├── remote/         Retrofit, API models  
│   ├── repository/     Single source of truth
│   └── sync/           WorkManager
├── di/                 Hilt modules
├── ui/                 Compose screens, ViewModels
└── HealthSyncApp.kt    Hilt root, WorkManager factory
```


