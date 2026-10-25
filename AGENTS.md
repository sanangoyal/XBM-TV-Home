# Agents

Cleaning a user's TV: follow [`PROMPT.md`](PROMPT.md) and [`skill/SKILL.md`](skill/SKILL.md). Use the scripts in `scripts/`; do not invent new ones. Disable, never uninstall. Back up first. Verify with TV screenshots.

Building for another TV: follow [`docs/PORTING.md`](docs/PORTING.md). The device OS decides the Settings content and order; this repo only gives the structure and look.

Changing this repo: edit `web/xmb.tpl.html` (UI) or `android/src` (native), rebuild with `python3 tools/mk.py && bash android/build.sh`, test on a real TV, keep `docs/DESIGN.md` and `docs/TECH.md` current. Never commit API keys, keystores, APKs, backups, or screenshots with personal data.
