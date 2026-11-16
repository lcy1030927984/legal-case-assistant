from pydantic import BaseModel, Field
from datetime import datetime
import uuid


class AgentStepRecord(BaseModel):
    step_number: int
    thought: str = ""
    tool_name: str | None = None
    tool_input: dict | None = None
    tool_output: str | None = None
    duration_ms: int = 0


class AgentTrace(BaseModel):
    trace_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    user_message: str
    steps: list[AgentStepRecord] = []
    final_answer: str = ""
    total_tokens: int = 0
    total_duration_ms: int = 0
    created_at: datetime = Field(default_factory=datetime.now)
