"""Pytest fixtures for cortex_mem tests."""

import pytest
import responses

from cortex_mem import CortexMemClient

BASE_URL = "http://localhost:37777"


@pytest.fixture
def client():
    """Create a client pointing at the mock server."""
    c = CortexMemClient(base_url=BASE_URL)
    yield c
    c.close()


@pytest.fixture
def mocked():
    """Activate responses mock."""
    with responses.RequestsMock() as rsps:
        yield rsps
