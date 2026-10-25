"""The error message: either side reports a message it cannot accept."""

from typing import Annotated, Literal

from pydantic import Field, StringConstraints

from machiavillager.domain.protocol.common import MAX_DETAIL_LENGTH, EnvelopeModel, ErrorCode


class Error(EnvelopeModel):
    """Either side reports a message it cannot accept. The code set is closed for branching."""

    type: Literal["error"] = Field(description='Message kind. Always "error".')
    code: ErrorCode = Field(
        description=(
            "Why the message was refused, from a closed set the other side can branch on. "
            "bad_message: could not be parsed or broke a rule. "
            "unsupported_protocol: the protocol version is not supported. "
            "unauthorized: the token was wrong or missing. "
            "internal: the sender failed unexpectedly."
        )
    )
    detail: Annotated[str, StringConstraints(max_length=MAX_DETAIL_LENGTH)] = Field(
        description=(
            "Short human-readable explanation, up to 500 characters. It never repeats the "
            'rejected payload. Example: "action args do not match the speak schema".'
        )
    )
