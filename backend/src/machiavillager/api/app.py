from fastapi import FastAPI

from machiavillager import __version__
from machiavillager.api.bridge import ModConnections, router


def create_app() -> FastAPI:
    app = FastAPI(title="Machiavillager", version=__version__)

    # One slot for the single mod. On app.state so every app, including each test's, has its own.
    app.state.mod_connections = ModConnections()
    app.include_router(router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok", "version": __version__}

    return app


app = create_app()
