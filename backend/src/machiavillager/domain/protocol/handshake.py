"""Handshake messages: hello from the mod, welcome from the backend.

New handshake messages go here.
"""

from typing import Annotated, Literal

from pydantic import Field, StringConstraints

from machiavillager.domain.protocol.common import MAX_MOD_VERSION_LENGTH, EnvelopeModel, WireVersion


class Hello(EnvelopeModel):
    """Sent by the mod after connecting. Carries its mod version and the protocol it speaks."""

    type: Literal["hello"] = Field(description='Message kind. Always "hello".')
    mod_version: Annotated[
        str, StringConstraints(min_length=1, max_length=MAX_MOD_VERSION_LENGTH)
    ] = Field(
        description=('Version of the mod, read from its own metadata by the mod. Example: "0.1.0".')
    )
    protocol: WireVersion = Field(
        description="Protocol version the mod speaks. Only 1 is accepted today."
    )


class Welcome(EnvelopeModel):
    """Sent by the backend in answer to hello when the protocol version is supported."""

    type: Literal["welcome"] = Field(description='Message kind. Always "welcome".')
    protocol: WireVersion = Field(
        description="Protocol version the backend agreed to speak. Always 1 today."
    )
