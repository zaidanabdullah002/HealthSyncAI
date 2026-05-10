import asyncio
import json
import os
import chromadb
from contextlib import asynccontextmanager
from pathlib import Path

import asyncpg
from fastapi import FastAPI
from openai import RateLimitError

from database import (
    DATABASE_URL,
    get_global_summary,
    get_summary_for_device,
    get_todays_steps,
    get_weekly_summary,
    set_goal,
    detect_anomaly,
    generate_daily_plan,
    init_db,
    save_events,
)
from models import (
    AgentChatRequest,
    AgentChatResponse,
    HealthSummary,
    SyncRequest,
    SyncResponse,
)
chroma_client = chromadb.PersistentClient()
memory_collection = chroma_client.get_or_create_collection(name="agent_memory")

from openai import OpenAI
openai_api_key = os.getenv("OPENAI_API_KEY")
client = OpenAI(api_key=openai_api_key) if openai_api_key else None
DEFAULT_STEP_GOAL = 10000


@asynccontextmanager
async def lifespan(app: FastAPI):
    for attempt in range(10):
        try:
            app.state.conn = await asyncpg.connect(DATABASE_URL)
            break
        except Exception:
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
    return await get_global_summary(conn, 0, 2**63 - 1)


@app.get("/health/{deviceId}/summary", response_model=HealthSummary)
async def summary_by_device_id(deviceId: str):
    conn = app.state.conn
    return await get_summary_for_device(conn, deviceId)


@app.post("/agent/chat", response_model=AgentChatResponse)
async def agent_chat(request: AgentChatRequest):
    conn = app.state.conn
    return await _run_agent_chat(conn, request)

def _memory_id(request):
    return request.chatId or f"{request.userId}:{request.deviceId or 'all'}"

def _read_memory(memory_id):
    try:
        result = memory_collection.get(ids=[memory_id], include=["documents"])
    except Exception:
        return None
    documents = result.get("documents") or []
    return documents[0] if documents else None


def _write_memory(memory_id, message, steps_today):
    try:
        payload = json.dumps({"latestMessage": message, "stepsToday": steps_today})
        vector = [0.0] * 8
        for idx, ch in enumerate(payload.encode("utf-8")):
            vector[idx % 8] += float(ch)
        memory_collection.upsert(
            ids=[memory_id],
            embeddings=[vector],
            documents=[payload],
            metadatas=[{"type": "chat_state"}],
        )
    except Exception:
        # Memory is best-effort for now; never block chat on it.
        return


async def _fallback_agent_chat(conn: asyncpg.Connection, request: AgentChatRequest) -> AgentChatResponse:
    steps_today = await get_todays_steps(
        conn,
        device_id=request.deviceId,
        timezone_offset_minutes=request.timezoneOffsetMinutes,
    )
    if steps_today >= DEFAULT_STEP_GOAL:
        assistant_text = f"Yes. You are on track today with {int(steps_today)} steps."
    else:
        remaining = DEFAULT_STEP_GOAL - int(steps_today)
        assistant_text = (
            f"You have {int(steps_today)} steps today. "
            f"You are {remaining} steps away from the {DEFAULT_STEP_GOAL} step goal."
        )

    memory_id = _memory_id(request)
    _write_memory(memory_id, request.message, steps_today)
    return AgentChatResponse(
        assistantResponse=assistant_text,
        stepsToday=steps_today,
        memoryId=memory_id,
    )


async def _run_agent_chat(conn: asyncpg.Connection, request: AgentChatRequest) -> AgentChatResponse:
    if client is None:
        return await _fallback_agent_chat(conn, request)

    memory_id = _memory_id(request)
    prior_memory = _read_memory(memory_id)
    steps_today: float | None = None

    context_text = json.dumps(
        {
            "userId": request.userId,
            "deviceId": request.deviceId,
            "timezoneOffsetMinutes": request.timezoneOffsetMinutes,
        }
    )

    tool_definitions = [
        {
            "type": "function",
            "name": "get_todays_steps",
            "description": "Get today's step count for the user's device, or all devices if none is provided.",
            "parameters": {
                "type": "object",
                "properties": {
                    "deviceId": {"type": ["string", "null"]},
                    "timezoneOffsetMinutes": {"type": "integer"},
                },
                "required": ["timezoneOffsetMinutes"],
                "additionalProperties": False,
            },
        }
        ,
        {
            "type": "function",
            "name": "get_weekly_summary",
            "description": "Get the last 7 days of health totals for the user's device, or all devices if none is provided.",
            "parameters": {
                "type": "object",
                "properties": {
                    "deviceId": {"type": ["string", "null"]},
                    "timezoneOffsetMinutes": {"type": "integer"},
                },
                "required": ["timezoneOffsetMinutes"],
                "additionalProperties": False,
            },
        }
        ,
        {
            "type": "function",
            "name": "set_goal",
            "description": "Save a health goal with a target step count and deadline.",
            "parameters": {
                "type": "object",
                "properties": {
                    "targetSteps": {"type": "number"},
                    "deadline": {"type": "string"},
                    "deviceId": {"type": ["string", "null"]},
                },
                "required": ["targetSteps", "deadline"],
                "additionalProperties": False,
            },
        }
        ,
        {
            "type": "function",
            "name": "detect_anomaly",
            "description": "Check whether today's activity is abnormal compared to the recent weekly pattern.",
            "parameters": {
                "type": "object",
                "properties": {
                    "deviceId": {"type": ["string", "null"]},
                    "timezoneOffsetMinutes": {"type": "integer"},
                },
                "required": ["timezoneOffsetMinutes"],
                "additionalProperties": False,
            },
        }
        ,
        {
            "type": "function",
            "name": "generate_daily_plan",
            "description": "Generate a simple daily health plan based on the current goal and today's progress.",
            "parameters": {
                "type": "object",
                "properties": {
                    "deviceId": {"type": ["string", "null"]},
                    "timezoneOffsetMinutes": {"type": "integer"},
                },
                "required": ["timezoneOffsetMinutes"],
                "additionalProperties": False,
            },
        }
    ]

    prompt = (
        f"User question: {request.message}\n"
        f"User context: {context_text}\n"
        f"Recent memory: {prior_memory or 'none'}\n"
        "If the question is about today's activity, call get_todays_steps before answering."
    )

    try:
        response = client.responses.create(
            model="gpt-5.4",
            input=prompt,
            tools=tool_definitions,
        )
    except (RateLimitError, Exception):
        return await _fallback_agent_chat(conn, request)

    while True:
        tool_calls = [item for item in response.output if item.type == "function_call"]
        if not tool_calls:
            break

        tool_outputs = []

        for call in tool_calls:
            if call.name == "get_todays_steps":
                args = json.loads(call.arguments or "{}")
                steps_today = await get_todays_steps(
                    conn,
                    device_id=args.get("deviceId") or request.deviceId,
                    timezone_offset_minutes=args.get("timezoneOffsetMinutes", request.timezoneOffsetMinutes),
                )
                tool_outputs.append(
                    {
                        "type": "function_call_output",
                        "call_id": call.call_id,
                        "output": json.dumps({"stepsToday": steps_today}),
                    }
                )
            if call.name == "get_weekly_summary":
                args = json.loads(call.arguments or "{}")
                weekly_summary = await get_weekly_summary(
                    conn,
                    device_id=args.get("deviceId") or request.deviceId,
                    timezone_offset_minutes=args.get("timezoneOffsetMinutes", request.timezoneOffsetMinutes),
                )
                tool_outputs.append(
                    {
                        "type": "function_call_output",
                        "call_id": call.call_id,
                        "output": json.dumps(weekly_summary),
                    }
                )
            if call.name == "set_goal":
                args = json.loads(call.arguments or "{}")
                goal = await set_goal(
                    conn,
                    user_id=request.userId,
                    target_steps=float(args.get("targetSteps", 10000)),
                    deadline=str(args.get("deadline", "")),
                    device_id=args.get("deviceId") or request.deviceId,
                )
                tool_outputs.append(
                    {
                        "type": "function_call_output",
                        "call_id": call.call_id,
                        "output": json.dumps(goal),
                    }
                )
            if call.name == "detect_anomaly":
                args = json.loads(call.arguments or "{}")
                anomaly = await detect_anomaly(
                    conn,
                    device_id=args.get("deviceId") or request.deviceId,
                    timezone_offset_minutes=args.get("timezoneOffsetMinutes", request.timezoneOffsetMinutes),
                )
                tool_outputs.append(
                    {
                        "type": "function_call_output",
                        "call_id": call.call_id,
                        "output": json.dumps(anomaly),
                    }
                )
            if call.name == "generate_daily_plan":
                args = json.loads(call.arguments or "{}")
                plan = await generate_daily_plan(
                    conn,
                    user_id=request.userId,
                    device_id=args.get("deviceId") or request.deviceId,
                    timezone_offset_minutes=args.get("timezoneOffsetMinutes", request.timezoneOffsetMinutes),
                )
                tool_outputs.append(
                    {
                        "type": "function_call_output",
                        "call_id": call.call_id,
                        "output": json.dumps(plan),
                    }
                )

        try:
            response = client.responses.create(
                model="gpt-5.4",
                previous_response_id=response.id,
                input=tool_outputs,
                tools=tool_definitions,
            )
        except (RateLimitError, Exception):
            return await _fallback_agent_chat(conn, request)

    assistant_text = response.output_text.strip()
    _write_memory(memory_id, request.message, steps_today)

    return AgentChatResponse(
        assistantResponse=assistant_text,
        stepsToday=steps_today,
        memoryId=memory_id,
    )
