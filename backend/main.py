import asyncio

from fastapi import FastAPI
from contextlib import asynccontextmanager
import asyncpg
from database import init_db, save_events, get_summary, DATABASE_URL, get_summary_for_device
from models import SyncRequest, SyncResponse, HealthSummary


@asynccontextmanager
async def lifespan(app: FastAPI):
    # retry connecting — PostgreSQL might not be ready immediately
    for attempt in range(10):
        try:
            app.state.conn = await asyncpg.connect(DATABASE_URL)
            break
        except Exception as e:
            print(f"DB not ready, retrying in 2s... (attempt {attempt + 1}/10)")
            await asyncio.sleep(2)
    else:
        raise Exception("Could not connect to database after 10 attempts")

    await init_db(app.state.conn)

    yield

    await app.state.conn.close()


app = FastAPI(lifespan=lifespan)


@app.post("/sync", response_model=SyncResponse)
async def sync(request: SyncRequest):
    conn = app.state.conn
    synced, conflicts = await save_events(conn, request.events)
    return SyncResponse(syncedCount=synced, conflictCount=conflicts)

@app.get("/health/summary")
async def summary():
    conn = app.state.conn
    return get_summary(conn)


@app.get("/health/{deviceId}/summary", response_model=HealthSummary)
async def summary_by_device_id(deviceId: str):
    conn = app.state.conn
    return await get_summary_for_device(conn, deviceId)