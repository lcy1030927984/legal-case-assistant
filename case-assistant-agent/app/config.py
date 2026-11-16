from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # LLM
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    llm_model: str = "gpt-4o"

    # Embedding
    embedding_model: str = "text-embedding-3-small"
    embedding_dim: int = 1536

    # PostgreSQL
    pg_host: str = "localhost"
    pg_port: int = 5432
    pg_database: str = "case_assistant_db"
    pg_user: str = "postgres"
    pg_password: str = "postgres"

    # Java 后端
    java_backend_url: str = "http://localhost:8080"

    # Dify
    dify_base_url: str = "http://localhost/v1"
    dify_workflow_key: str = ""

    # RAG
    legal_docs_dir: str = "./docs/legal"

    # Agent
    agent_max_steps: int = 10

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}

    @property
    def pg_connection_string(self) -> str:
        return (
            f"postgresql://{self.pg_user}:{self.pg_password}"
            f"@{self.pg_host}:{self.pg_port}/{self.pg_database}"
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
