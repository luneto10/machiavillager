import logging

from fastapi import FastAPI

from machiavillager import __version__
from machiavillager.api.bridge import ModConnections, router
from machiavillager.api.bridge.requests import ModGameBridge, PendingRequests
from machiavillager.api.dev import router as dev_router
from machiavillager.infrastructure.settings import Settings, get_settings

# Same shape as uvicorn's own lines, so app and server logs read alike.
LOG_FORMAT = "%(levelname)s:     %(name)s - %(message)s"
LOG_HANDLER_NAME = "machiavillager"


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
    app.include_router(router)

    if effective.dev_endpoints:
        app.include_router(dev_router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok", "version": __version__}

    return app


app = create_app()
