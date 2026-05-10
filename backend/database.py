import os
from datetime import datetime, timedelta, timezone

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
    await conn.execute("""
        CREATE TABLE IF NOT EXISTS health_goals (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            device_id TEXT,
            target_steps FLOAT NOT NULL,
            deadline TEXT NOT NULL,
            created_at BIGINT NOT NULL
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


async def get_todays_steps(conn, device_id: str | None = None, timezone_offset_minutes: int = 0) -> float:
    now_utc = datetime.now(timezone.utc)
    local_now = now_utc + timedelta(minutes=timezone_offset_minutes)
    local_start = local_now.replace(hour=0, minute=0, second=0, microsecond=0)
    local_end = local_start + timedelta(days=1)

    start_utc = local_start - timedelta(minutes=timezone_offset_minutes)
    end_utc = local_end - timedelta(minutes=timezone_offset_minutes)
    start_ms = int(start_utc.timestamp() * 1000)
    end_ms = int(end_utc.timestamp() * 1000)

    if device_id:
        result = await conn.fetchrow(
            """
            SELECT COALESCE(MAX(value), 0) AS steps
            FROM health_events
            WHERE device_id = $1
              AND type = 'STEPS'
              AND timestamp >= $2
              AND timestamp < $3
            """,
            device_id,
            start_ms,
            end_ms,
        )
    else:
        result = await conn.fetchrow(
            """
            SELECT COALESCE(MAX(value), 0) AS steps
            FROM health_events
            WHERE type = 'STEPS'
              AND timestamp >= $1
              AND timestamp < $2
            """,
            start_ms,
            end_ms,
        )

    return float(result["steps"] or 0.0)


async def get_weekly_summary(conn, device_id: str | None = None, timezone_offset_minutes: int = 0) -> dict:
    now_utc = datetime.now(timezone.utc)
    local_now = now_utc + timedelta(minutes=timezone_offset_minutes)
    local_start = local_now.replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=6)
    local_end = (local_now.replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=1))

    start_utc = local_start - timedelta(minutes=timezone_offset_minutes)
    end_utc = local_end - timedelta(minutes=timezone_offset_minutes)
    start_ms = int(start_utc.timestamp() * 1000)
    end_ms = int(end_utc.timestamp() * 1000)

    if device_id:
        result = await conn.fetchrow(
            """
            SELECT
                COALESCE(SUM(CASE WHEN type = 'STEPS' THEN value ELSE 0 END), 0) AS steps,
                COALESCE(SUM(CASE WHEN type = 'ACTIVE_TIME' THEN value ELSE 0 END), 0) AS active_time,
                COALESCE(SUM(CASE WHEN type = 'CALORIES' THEN value ELSE 0 END), 0) AS calories
            FROM health_events
            WHERE device_id = $1
              AND timestamp >= $2
              AND timestamp < $3
            """,
            device_id,
            start_ms,
            end_ms,
        )
    else:
        result = await conn.fetchrow(
            """
            SELECT
                COALESCE(SUM(CASE WHEN type = 'STEPS' THEN value ELSE 0 END), 0) AS steps,
                COALESCE(SUM(CASE WHEN type = 'ACTIVE_TIME' THEN value ELSE 0 END), 0) AS active_time,
                COALESCE(SUM(CASE WHEN type = 'CALORIES' THEN value ELSE 0 END), 0) AS calories
            FROM health_events
            WHERE timestamp >= $1
              AND timestamp < $2
            """,
            start_ms,
            end_ms,
        )

    return {
        "steps": float(result["steps"] or 0.0),
        "activeTime": float(result["active_time"] or 0.0),
        "calories": float(result["calories"] or 0.0),
    }


async def set_goal(conn, user_id: str, target_steps: float, deadline: str, device_id: str | None = None) -> dict:
    goal_id = f"{user_id}:{device_id or 'all'}"
    await conn.execute(
        """
        INSERT INTO health_goals (id, user_id, device_id, target_steps, deadline, created_at)
        VALUES ($1, $2, $3, $4, $5, $6)
        ON CONFLICT (id) DO UPDATE
        SET target_steps = EXCLUDED.target_steps,
            deadline = EXCLUDED.deadline,
            created_at = EXCLUDED.created_at
        """,
        goal_id,
        user_id,
        device_id,
        target_steps,
        deadline,
        int(datetime.now(timezone.utc).timestamp() * 1000),
    )
    return {
        "goalId": goal_id,
        "userId": user_id,
        "deviceId": device_id,
        "targetSteps": float(target_steps),
        "deadline": deadline,
    }


async def get_goal(conn, user_id: str, device_id: str | None = None) -> dict | None:
    goal_id = f"{user_id}:{device_id or 'all'}"
    result = await conn.fetchrow(
        """
        SELECT user_id, device_id, target_steps, deadline, created_at
        FROM health_goals
        WHERE id = $1
        """,
        goal_id,
    )
    if not result:
        return None
    return {
        "goalId": goal_id,
        "userId": result["user_id"],
        "deviceId": result["device_id"],
        "targetSteps": float(result["target_steps"]),
        "deadline": result["deadline"],
        "createdAt": int(result["created_at"]),
    }


async def detect_anomaly(conn, device_id: str | None = None, timezone_offset_minutes: int = 0) -> dict:
    weekly = await get_weekly_summary(conn, device_id=device_id, timezone_offset_minutes=timezone_offset_minutes)
    today = await get_todays_steps(conn, device_id=device_id, timezone_offset_minutes=timezone_offset_minutes)
    average_daily_steps = weekly["steps"] / 7.0
    low_activity = average_daily_steps > 0 and today < (average_daily_steps * 0.5)

    if low_activity:
        return {
            "anomalyDetected": True,
            "type": "LOW_ACTIVITY",
            "message": "Today's activity is much lower than your recent average.",
            "todaySteps": float(today),
            "averageDailySteps": float(average_daily_steps),
        }

    return {
        "anomalyDetected": False,
        "type": "NONE",
        "message": "No anomaly detected from your recent activity pattern.",
        "todaySteps": float(today),
        "averageDailySteps": float(average_daily_steps),
    }


async def generate_daily_plan(conn, user_id: str, device_id: str | None = None, timezone_offset_minutes: int = 0) -> dict:
    goal = await get_goal(conn, user_id=user_id, device_id=device_id)
    today = await get_todays_steps(conn, device_id=device_id, timezone_offset_minutes=timezone_offset_minutes)
    target_steps = float(goal["targetSteps"]) if goal else float(10000)
    remaining = max(0.0, target_steps - today)
    suggested_walk = min(max(1000.0, remaining * 0.25), remaining) if remaining > 0 else 0.0

    return {
        "goal": goal,
        "todaySteps": float(today),
        "targetSteps": target_steps,
        "remainingSteps": float(remaining),
        "plan": [
            f"Walk {int(suggested_walk)} steps in one focused session.",
            "Take 2 short movement breaks after meals.",
            "Check progress again this evening.",
        ],
    }
