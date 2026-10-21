from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AGENT_", env_file=".env", extra="ignore")
    java_url: str = "http://127.0.0.1:8080"
    data_dir: Path = Path("data")
    cors_origins: str = "http://localhost:5173,http://localhost:5174"
    api_key: str = ""
    base_url: str = "https://api.openai.com/v1"
    model: str = ""
    embedding_model: str = ""
    rag_chunk_size: int = 900
    rag_chunk_overlap: int = 150
    rag_top_k: int = 6
    rag_similarity_threshold: float = 0.35
    timeout_seconds: int = 60
    max_steps: int = 30
    max_tool_calls: int = 48
    max_output_tokens: int = 4096
    max_run_seconds: int = 180
    max_concurrent_runs: int = 4


settings = Settings()
