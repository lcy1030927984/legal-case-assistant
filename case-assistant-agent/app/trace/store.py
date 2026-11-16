import json
from pathlib import Path
from datetime import datetime

from app.models.trace import AgentTrace, AgentStepRecord


class TraceStore:
    """Trace 持久化存储，使用本地 JSON 文件"""

    def __init__(self, storage_dir: str = "./data/traces"):
        self._storage_dir = Path(storage_dir)
        self._storage_dir.mkdir(parents=True, exist_ok=True)
        self._traces: dict[str, AgentTrace] = {}
        self._load_existing()

    def _load_existing(self):
        """启动时加载已有 trace"""
        for f in self._storage_dir.glob("*.json"):
            try:
                data = json.loads(f.read_text(encoding="utf-8"))
                trace = AgentTrace(**data)
                self._traces[trace.trace_id] = trace
            except Exception:
                continue

    def save(self, trace: AgentTrace):
        """保存一条 trace"""
        self._traces[trace.trace_id] = trace
        file_path = self._storage_dir / f"{trace.trace_id}.json"
        file_path.write_text(
            trace.model_dump_json(indent=2),
            encoding="utf-8",
        )

    def get(self, trace_id: str) -> AgentTrace | None:
        """根据 ID 获取 trace"""
        return self._traces.get(trace_id)

    def list_traces(self, page: int = 1, size: int = 20) -> list[AgentTrace]:
        """分页查询 trace 列表，按创建时间倒序"""
        sorted_traces = sorted(
            self._traces.values(),
            key=lambda t: t.created_at,
            reverse=True,
        )
        start = (page - 1) * size
        return sorted_traces[start: start + size]

    def count(self) -> int:
        return len(self._traces)


# 全局单例
_trace_store: TraceStore | None = None


def get_trace_store() -> TraceStore:
    global _trace_store
    if _trace_store is None:
        _trace_store = TraceStore()
    return _trace_store
