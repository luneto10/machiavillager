import logging
import time

from fastapi import FastAPI

from machiavillager import __version__
from machiavillager.api.bridge import ModConnections, router
from machiavillager.api.bridge.ids import new_request_id
from machiavillager.api.bridge.requests import ModGameBridge, PendingRequests
from machiavillager.api.dev import router as dev_router
from machiavillager.application.conversation_store import ConversationStore
from machiavillager.application.handle_lines_needed import HandleLinesNeeded
from machiavillager.application.handle_player_chat import HandlePlayerChat
from machiavillager.application.ports import GameBridge, ToneJudge
from machiavillager.infrastructure.llm_batch_writer import LlmBatchWriter
from machiavillager.infrastructure.llm_tone_judge import LlmToneJudge
from machiavillager.infrastructure.llm_writer import LlmLineWriter
from machiavillager.infrastructure.persona import read_persona
from machiavillager.infrastructure.settings import Settings, get_settings

# Same shape as uvicorn's own lines, so app and server logs read alike.
LOG_FORMAT = "%(levelname)s:     %(name)s - %(message)s"
LOG_HANDLER_NAME = "machiavillager"

logger = logging.getLogger(__name__)


def _configure_logging() -> None:
    # Our loggers have no handler of their own, so uvicorn would drop their INFO lines.
    # Configured once: tests call create_app many times, and handlers must not stack.
    logger = logging.getLogger("machiavillager")
    if any(handler.name == LOG_HANDLER_NAME for handler in logger.handlers):
        return
    handler = logging.StreamHandler()
    handler.name = LOG_HANDLER_NAME
    handler.setFormatter(logging.Formatter(LOG_FORMAT))
    logger.addHandler(handler)
    logger.setLevel(logging.INFO)
    # Without this, every line would also reach the root logger and print twice under uvicorn.
    logger.propagate = False


def _build_tone_judge(settings: Settings) -> ToneJudge | None:
    """The tone judge, or None when no judge model has a key to classify a line."""

    judge = LlmToneJudge.from_settings(settings)
    return judge if judge.has_models else None


def _build_player_chat(
    settings: Settings,
    bridge: GameBridge,
    writer: LlmLineWriter,
    tone_judge: ToneJudge | None,
) -> HandlePlayerChat | None:
    """The AI reply to player chat, or None when no model has a key to write it."""

    if not writer.has_models:
        logger.info("no LLM key set: player chat gets no AI reply")
        return None
    return HandlePlayerChat(
        writer,
        bridge,
        ConversationStore(),
        read_persona,
        settings.profanity,
        new_request_id,
        time.monotonic,
        # Dev only: replies say which model wrote them, and the dev routes are the only switch.
        show_model=settings.dev_endpoints,
        tone_judge=tone_judge,
    )


def _build_lines_needed(settings: Settings, bridge: GameBridge) -> HandleLinesNeeded | None:
    """The batch handler for lines_needed, or None when no batch model has a key to write it."""

    writer = LlmBatchWriter.from_settings(settings)
    if not writer.has_models:
        logger.info("no LLM key set: no line batches are written")
        return None
    return HandleLinesNeeded(
        writer,
        bridge,
        new_request_id,
        time.monotonic,
        batches_per_hour=settings.lines_batches_per_hour,
        refresh_seconds=settings.lines_refresh_seconds,
        retry_seconds=settings.lines_retry_seconds,
    )


def create_app(settings: Settings | None = None) -> FastAPI:
    _configure_logging()
    # Read once, here: the dev routes are mounted or absent for the whole app, never per request.
    effective = settings if settings is not None else get_settings()
    app = FastAPI(title="Machiavillager", version=__version__)

    # One slot for the single mod. On app.state so every app, including each test's, has its own.
    connections = ModConnections()
    pending = PendingRequests()
    app.state.mod_connections = connections
    app.state.pending_requests = pending
    app.state.game_bridge = ModGameBridge(connections, pending)
    # Read by handle_event: when on, he repeats each player chat line back.
    app.state.dev_echo = effective.dev_echo
    # Read by handle_event: dev_model switches the model on this writer, so it stays on state.
    writer = LlmLineWriter.from_settings(effective)
    app.state.llm_writer = writer
    # Read by handle_event and the welcome: the dev-only model switch and suggestions need this.
    app.state.dev_endpoints = effective.dev_endpoints
    # Read by handle_event: when set, a player's chat line gets his AI reply.
    # Built once; the judge runs beside the reply, so it is only passed to the chat handler.
    tone_judge = _build_tone_judge(effective)
    app.state.handle_player_chat = _build_player_chat(
        effective, app.state.game_bridge, writer, tone_judge
    )
    # Read by handle_event: when set, a lines_needed event gets a batch written and sent.
    app.state.handle_lines_needed = _build_lines_needed(effective, app.state.game_bridge)
    app.include_router(router)

    if effective.dev_endpoints:
        app.include_router(dev_router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok", "version": __version__}

    return app


app = create_app()
