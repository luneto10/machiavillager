import logging

from fastapi import FastAPI

from machiavillager import __version__
from machiavillager.api.bridge import ModConnections, router

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


def create_app() -> FastAPI:
    _configure_logging()
    app = FastAPI(title="Machiavillager", version=__version__)

    # One slot for the single mod. On app.state so every app, including each test's, has its own.
    app.state.mod_connections = ModConnections()
    app.include_router(router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok", "version": __version__}

    return app


app = create_app()
