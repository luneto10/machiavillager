"""Application layer: use cases (handle chat message, run agent turn, store memory)
and the ports (Protocol classes) they need, e.g. MemoryRepository, LanguageModel, GameBridge.

May import `domain` only. Concrete adapters are injected from the outside.
"""
