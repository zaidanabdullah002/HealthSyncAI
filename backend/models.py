from pydantic import BaseModel

class HealthEvent(BaseModel):
    id: str
    deviceId: str
    type: str #"STEPS", "ACTIVE_TIME", "CALORIES"ue: Double, // 5000.0
    value: float
    timestamp: int
    isSynced: bool = False

class SyncRequest(BaseModel):
    events: list[HealthEvent]

class SyncResponse(BaseModel):
    syncedCount: int
    conflictCount: int

class HealthSummary(BaseModel):
    steps: float
    activeTime: float
    calories: float