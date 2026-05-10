import asyncio
import json
import os
import re
import chromadb
from contextlib import asynccontextmanager
from pathlib import Path

import asyncpg
from fastapi import FastAPI
from openai import RateLimitError

from database import (
    DATABASE_URL,
    get_global_summary,
    get_goal,
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


def _normalized_message(message: str) -> str:
    return message.strip().lower()


def _looks_like_weekly_question(message: str) -> bool:
    text = _normalized_message(message)
    return "week" in text or "weekly" in text or "7 day" in text or "7-day" in text


def _looks_like_goal_question(message: str) -> bool:
    text = _normalized_message(message)
    return "goal" in text or "set a goal" in text or "set goal" in text or "deadline" in text


def _looks_like_anomaly_question(message: str) -> bool:
    text = _normalized_message(message)
    return "anomaly" in text or "abnormal" in text or "off track" in text


def _looks_like_plan_question(message: str) -> bool:
    text = _normalized_message(message)
    return "daily plan" in text or "make me a plan" in text or "plan for today" in text or "what should i do today" in text


def _looks_like_today_question(message: str) -> bool:
    text = _normalized_message(message)
    return "today" in text or "on track" in text or "steps" in text


def _parse_goal_request(message: str) -> tuple[float | None, str | None]:
    text = _normalized_message(message)

    target_steps: float | None = None
    target_match = re.search(r"(\d[\d,]*\s*[kK]?)\s*(?:steps?|step goal|goal|target)?", text)
    if target_match:
        raw_target = target_match.group(1).replace(",", "").strip().lower()
        try:
            if raw_target.endswith("k"):
                target_steps = float(raw_target[:-1]) * 1000.0
            else:
                target_steps = float(raw_target)
        except ValueError:
            target_steps = None

    deadline: str | None = None
    deadline_match = re.search(r"\b(?:by|for)\s+([a-z0-9][a-z0-9 ,/-]*)", text)
    if deadline_match:
        deadline = deadline_match.group(1).strip()

    if deadline is None:
        if "tomorrow" in text:
            deadline = "tomorrow"
        elif "today" in text:
            deadline = "today"
        elif "this week" in text:
            deadline = "this week"

    return target_steps, deadline


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
    memory_id = _memory_id(request)
    message = request.message.strip()
    normalized = _normalized_message(message)
    steps_today = None

    if _looks_like_plan_question(message):
        plan = await generate_daily_plan(
            conn,
            user_id=request.userId,
            device_id=request.deviceId,
            timezone_offset_minutes=request.timezoneOffsetMinutes,
        )
        steps_today = plan["todaySteps"]
        plan_lines = "\n".join(f"- {item}" for item in plan["plan"])
        goal_text = ""
        if plan["goal"]:
            goal_text = f" Your saved goal is {int(plan['targetSteps'])} steps by {plan['goal']['deadline']}."
        assistant_text = (
            f"Here’s your daily plan.\n"
            f"You’ve done {int(plan['todaySteps'])} steps today and have {int(plan['remainingSteps'])} steps left to reach {int(plan['targetSteps'])}.\n"
            f"{goal_text}\n"
            f"{plan_lines}"
        )
    elif _looks_like_goal_question(message):
        if "set" in normalized and ("goal" in normalized or "target" in normalized):
            target_steps, deadline = _parse_goal_request(message)
            if target_steps is None:
                target_steps = float(DEFAULT_STEP_GOAL)
            if deadline is None:
                deadline = "today"
            goal = await set_goal(
                conn,
                user_id=request.userId,
                target_steps=target_steps,
                deadline=deadline,
                device_id=request.deviceId,
            )
            assistant_text = (
                f"Saved your goal for {int(goal['targetSteps'])} steps by {goal['deadline']}."
            )
        else:
            goal = await get_goal(conn, user_id=request.userId, device_id=request.deviceId)
            if goal:
                assistant_text = (
                    f"Your current goal is {int(goal['targetSteps'])} steps by {goal['deadline']}."
                )
            else:
                assistant_text = "You don’t have a saved goal yet. You can ask me to set one."
    elif _looks_like_anomaly_question(message):
        anomaly = await detect_anomaly(
            conn,
            device_id=request.deviceId,
            timezone_offset_minutes=request.timezoneOffsetMinutes,
        )
        steps_today = anomaly["todaySteps"]
        assistant_text = anomaly["message"]
        if anomaly["anomalyDetected"]:
            assistant_text += f" Today: {int(anomaly['todaySteps'])} steps vs recent average {int(anomaly['averageDailySteps'])}."
    elif _looks_like_weekly_question(message):
        weekly = await get_weekly_summary(
            conn,
            device_id=request.deviceId,
            timezone_offset_minutes=request.timezoneOffsetMinutes,
        )
        steps_today = await get_todays_steps(
            conn,
            device_id=request.deviceId,
            timezone_offset_minutes=request.timezoneOffsetMinutes,
        )
        assistant_text = (
            f"This week you’ve done {int(weekly['steps'])} steps total, "
            f"{int(weekly['activeTime'])} minutes of active time, and {int(weekly['calories'])} calories.\n"
            f"Today you’re at {int(steps_today)} steps."
        )
    else:
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
                target_steps = args.get("targetSteps")
                deadline = str(args.get("deadline", "")).strip()
                if target_steps is None:
                    parsed_target, parsed_deadline = _parse_goal_request(request.message)
                    target_steps = parsed_target if parsed_target is not None else float(10000)
                    if not deadline and parsed_deadline:
                        deadline = parsed_deadline
                if not deadline:
                    deadline = "today"
                goal = await set_goal(
                    conn,
                    user_id=request.userId,
                    target_steps=float(target_steps),
                    deadline=deadline,
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
