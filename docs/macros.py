"""
MkDocs macros hook — fetches latest release versions from GitHub API at build time.

Exposes Jinja2 variables:
  - {{ release_version }}  — latest release-v* tag (e.g. "1.0.0")
  - {{ sdk_version }}      — latest sdk-v* tag (e.g. "0.2.0")
  - {{ spi_version }}      — latest spi-v* tag (e.g. "1.0.15")
"""

import json
import os
import urllib.request

GITHUB_API = "https://api.github.com/repos/dsjkeeplearning/kos-auth-backend"
FALLBACK = "latest"


def _fetch_tags():
    """Fetch all tags from the GitHub API (paginated, up to 100)."""
    url = f"{GITHUB_API}/tags?per_page=100"
    headers = {"Accept": "application/vnd.github+json"}
    # Use GITHUB_TOKEN if available (avoids rate limiting in CI)
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode())
    except Exception:
        return []


def _latest_tag(tags, prefix):
    """Return the version string for the most recent tag matching *prefix*."""
    for tag in tags:
        name = tag.get("name", "")
        if name.startswith(prefix):
            return name[len(prefix):]
    return FALLBACK


def define_env(env):
    """Called by mkdocs-macros-plugin to register extra variables."""
    tags = _fetch_tags()

    env.variables["release_version"] = _latest_tag(tags, "release-v")
    env.variables["sdk_version"] = _latest_tag(tags, "sdk-v")
    env.variables["spi_version"] = _latest_tag(tags, "spi-v")
