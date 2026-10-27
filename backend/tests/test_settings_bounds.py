import pytest
from pydantic import ValidationError

from machiavillager.infrastructure.settings import Settings


@pytest.mark.parametrize(
    "field",
    ["lines_batches_per_hour", "lines_refresh_seconds", "lines_retry_seconds"],
)
def test_zero_is_refused_for_each_line_batch_limit(field):
    # _env_file=None keeps the test off the real .env, so only the value under test is read.
    with pytest.raises(ValidationError):
        Settings(_env_file=None, **{field: 0})
