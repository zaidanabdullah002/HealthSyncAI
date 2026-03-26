import asyncpg
import os
from models import HealthEvent, HealthSummary

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://postgres:postgres@localhost:5432/healthsync")

async def init_db(conn):
    await conn.execute("""
        CREATE TABLE IF NOT EXISTS health_events (
            id TEXT PRIMARY KEY,
            device_id TEXT NOT NULL,
            type TEXT NOT NULL,
            value FLOAT NOT NULL,
            timestamp BIGINT NOT NULL,
            is_synced BOOLEAN NOT NULL DEFAULT FALSE
        )
    """)

async def save_events(conn, events: list[HealthEvent]) -> tuple[int, int]:
    synced_count = 0
    conflict_count = 0

    for event in events:
        result = await conn.fetchrow("""
            INSERT INTO health_events (id, device_id, type, value, timestamp, is_synced)
            VALUES ($1, $2, $3, $4, $5, $6)
            ON CONFLICT (id) DO UPDATE
            SET value = GREATEST(health_events.value, EXCLUDED.value)
            RETURNING value, xmax
        """, event.id, event.deviceId, event.type,
            event.value, event.timestamp, event.isSynced)

        if result["xmax"] == 0:
            synced_count += 1
        elif result["value"] > event.value:
            conflict_count += 1
        else:
            synced_count += 1

    return synced_count, conflict_count

async def get_summary_for_device(conn, device_id: str) -> HealthSummary:
    result = await conn.fetchrow("""
        SELECT
            MAX(CASE WHEN type = 'STEPS' THEN value END) as steps,
            MAX(CASE WHEN type = 'ACTIVE_TIME' THEN value END) as active_time,
            MAX(CASE WHEN type = 'CALORIES' THEN value END) as calories
        FROM health_events
        WHERE device_id = $1
    """, device_id)

    return HealthSummary(
        steps=result["steps"] or 0.0,
        activeTime=result["active_time"] or 0.0,
        calories=result["calories"] or 0.0
    )

async def get_global_summary(conn, start: int, end: int) -> HealthSummary:
    result = await conn.fetchrow("""
        SELECT
            MAX(CASE WHEN type = 'STEPS' THEN value END) as steps,
            MAX(CASE WHEN type = 'ACTIVE_TIME' THEN value END) as active_time,
            MAX(CASE WHEN type = 'CALORIES' THEN value END) as calories
        FROM health_events
        WHERE timestamp >= $1 AND timestamp < $2
    """, start, end)

    return HealthSummary(
        steps=result["steps"] or 0.0,
        activeTime=result["active_time"] or 0.0,
        calories=result["calories"] or 0.0
    )