from machiavillager.application.conversation_store import (
    MAX_LINES,
    SILENCE_SECONDS,
    ConversationStore,
)
from machiavillager.application.prompt_builder import ConversationLine

KEY = "conv_01HZX8K2M9"


def _line(text: str, *, from_npc: bool = False) -> ConversationLine:
    return ConversationLine("Luciano", text, from_npc=from_npc)


def test_keeps_only_the_newest_lines():
    store = ConversationStore()

    for number in range(MAX_LINES + 3):
        store.add(KEY, _line(f"line {number}"), now=0.0)

    texts = [line.text for line in store.recent(KEY, now=0.0)]
    assert texts == [f"line {number}" for number in range(3, MAX_LINES + 3)]


def test_by_default_a_quiet_conversation_is_not_forgotten():
    store = ConversationStore()
    store.add(KEY, _line("hi"), now=0.0)

    assert len(store.recent(KEY, now=SILENCE_SECONDS * 1000)) == 1


def test_conversation_is_forgotten_after_a_minute_of_silence_when_enabled():
    store = ConversationStore(silence_seconds=SILENCE_SECONDS)
    store.add(KEY, _line("hi"), now=100.0)

    # Exactly the limit still counts; one second more and the conversation is over.
    assert len(store.recent(KEY, now=100.0 + SILENCE_SECONDS)) == 1
    assert store.recent(KEY, now=100.0 + SILENCE_SECONDS + 1) == []


def test_a_new_line_after_the_silence_starts_a_fresh_conversation():
    store = ConversationStore(silence_seconds=SILENCE_SECONDS)
    store.add(KEY, _line("old"), now=0.0)

    store.add(KEY, _line("new"), now=SILENCE_SECONDS * 10)

    assert [line.text for line in store.recent(KEY, now=SILENCE_SECONDS * 10)] == ["new"]


def test_an_active_conversation_does_not_expire_mid_talk():
    store = ConversationStore(silence_seconds=SILENCE_SECONDS)
    store.add(KEY, _line("first"), now=0.0)
    store.add(KEY, _line("second", from_npc=True), now=50.0)

    # Silence is counted from the newest line, so 100 s after the first is still only 50 s quiet.
    assert len(store.recent(KEY, now=100.0)) == 2
