# HealthSync AI

HealthSync AI is an offline-first health intelligence platform that treats mobile health data as a real distributed systems problem, not just a UI problem. It combines local-first Android data capture, conflict-safe multi-device sync, and an agent layer that can answer questions using live health data.

## What It Demonstrates

- **Local-first data model**: Health events are written to Room first, then synchronized asynchronously through WorkManager.
- **Conflict-safe sync**: Multi-device step totals use Max-Register semantics so concurrent updates converge without losing the highest observed value.
- **Clear separation of concerns**: UI, domain, local storage, networking, and sync are split into composable layers.
- **Production-minded Android stack**: Hilt, Retrofit, Coroutines, Flow, and Jetpack Compose are wired in a way that is testable and extensible.
- **Agent-ready backend**: FastAPI, PostgreSQL, and a tool-using chat endpoint provide a real foundation for AI-assisted health coaching.
- **Practical AI memory**: ChromaDB stores lightweight context instead of full chat logs, keeping the agent focused and cheap to run.

## System Overview

HealthSync AI is designed around a simple rule: the local database is the source of truth, and everything else is a view over that truth.

- Android captures health events immediately and persists them locally.
- WorkManager retries sync in the background when network access is available.
- The backend resolves incoming updates, aggregates summaries, and serves agent queries.
- The chat layer uses real health data to answer questions like:
  - Am I on track today?
  - Am I on track this week?
  - Make me a daily plan
  - Set a goal for Friday

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
| Backend | FastAPI + PostgreSQL |
| AI Agent | Claude API + Tool Use |
| Memory | ChromaDB |
| Deployment | Docker Compose, Railway |

## Core Engineering Problems Solved

### Multi-device Conflict Resolution
When the same user records activity on multiple devices while offline, each device can update the same logical metric independently. HealthSync AI uses Max-Register merge behavior so the system converges on the highest observed value without overwriting legitimate data.
```
Phone: 5,000 steps  ──→  max(5000, 8000) = 8,000 ✅
Watch: 8,000 steps  ──→
```

### Offline-first Architecture
Every health event is written locally first with `isSynced = false`. WorkManager handles retry logic and batch synchronization, which keeps the app usable even when the network is unreliable or absent.

### Scalable Device Model
Devices are not hardcoded into the UI. The dashboard derives device-specific summaries from persisted data, so new device types can be added without restructuring the screen logic.

### Agentic Health Q&A
The backend exposes a `/agent/chat` endpoint that can answer health questions using real data. If the model is unavailable, the server still responds with local PostgreSQL-backed logic so the experience remains functional.

## Why This Feels Senior

- The app does not treat sync as a “save button” problem.
- The backend does not treat AI as a toy chatbot.
- The architecture keeps the data model, sync engine, and assistant layer decoupled.
- The system can still answer useful questions when the AI provider is unavailable.
- The project is designed to survive real-world constraints: offline use, multiple devices, retries, and partial failure.

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

## Selected Capabilities

- Offline capture of health events
- Multi-device synchronization
- Conflict resolution with deterministic merges
- Backend summaries over PostgreSQL
- AI chat with tool use and lightweight memory
- Fallback responses powered by local data
- Chat UI optimized for mobile conversation flow

