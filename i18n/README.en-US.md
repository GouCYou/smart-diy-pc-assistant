# Smart DIY PC Assistant

A responsive Vue 3 PC-building application with a Python Agent backend (FastAPI, LangChain/LangGraph, RAG and JD product crawling), a Java business backend, and an Element Plus administration console.

The former uni-app client and Java AI/market services have been replaced. Business data remains in MySQL. Agent conversations, preferences, runs, events and graph checkpoints use persistent SQLite storage for a single-worker deployment. Semantic retrieval is optional and requires an embedding model.

See the [main README](../README.md) and [Agent documentation](../smart-diy-pc-assistant-agent/README.md) for setup, deployment, testing and explicit legacy data import. No legacy production tables are automatically dropped.
