from machiavillager.application.history_budget import (
    LINE_OVERHEAD_TOKENS,
    estimate_tokens,
    fit_history,
)
from machiavillager.application.prompt_builder import ConversationLine

PLAYER = "Luciano"
NPC = "Machiavillager"


def _player(text: str) -> ConversationLine:
    return ConversationLine(PLAYER, text, from_npc=False)


def _npc(text: str) -> ConversationLine:
    return ConversationLine(NPC, text, from_npc=True)


def test_estimate_tokens_counts_three_chars_per_token_rounding_up():
    assert estimate_tokens("") == 0
    assert estimate_tokens("a") == 1
    assert estimate_tokens("abc") == 1
    assert estimate_tokens("abcd") == 2
    assert estimate_tokens("abcdefg") == 3


def test_empty_history_stays_empty():
    assert fit_history([]) == []


def test_history_that_fits_comes_back_unchanged():
    lines = [_player("oi"), _npc("fala"), _player("tudo bem")]

    assert fit_history(lines) == lines


def test_oldest_lines_are_dropped_and_order_is_kept():
    # Each line costs 3 (speaker "Luciano") + 10 (text) + 8 (overhead) = 21 tokens.
    lines = [_player(f"{i:02d}" + "x" * 28) for i in range(5)]

    kept = fit_history(lines, max_tokens=42)

    assert kept == lines[3:]


def test_newest_line_is_kept_even_when_it_alone_is_over_budget():
    short = _player("oi")
    huge = _player("y" * 3000)

    assert fit_history([short, huge], max_tokens=10) == [huge]


def test_leading_npc_line_is_dropped_after_trimming():
    # Costs: old player line 21, his line 23 ("Machiavillager" is 5 tokens), new player line 21.
    old = _player("a" * 30)
    his = _npc("b" * 30)
    new = _player("c" * 30)

    kept = fit_history([old, his, new], max_tokens=50)

    assert kept == [new]


def test_overhead_is_counted_per_line():
    line = _player("abc")

    # Exactly the cost of one line fits; one token less does not, so only the newest line stays.
    cost = estimate_tokens(PLAYER) + estimate_tokens("abc") + LINE_OVERHEAD_TOKENS
    assert fit_history([line, line], max_tokens=cost) == [line]
    assert fit_history([line, line], max_tokens=cost * 2) == [line, line]
