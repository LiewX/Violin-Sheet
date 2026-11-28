"""PreToolUse hook (Bash and PowerShell): blocks commands that write repo files inline (workflow ideas 61, 84).

Blocked: `sed -i`/`--in-place`, `python -c` that writes a file (an open() write mode, write_text/bytes, or a
redirect of its output), a heredoc fed to Python, and a heredoc written into a file (`cat >`, `> file`, `tee`).
Allowed: read-only heredocs (commit messages, gh bodies), commands that only mention these words inside quotes or
a heredoc body, scripts run from a file, everything else.
Exit 2 blocks the call and shows stderr to the agent. Bad input fails open (allowed): the hook guards against
habits, not attacks.
"""
import json
import re
import sys

MESSAGE = (
    "Blocked by .claude/hooks/block_inline_edits.py: inline edits through `python -c`, a heredoc or `sed -i` "
    "silently miss or repeat changes. Use the Edit tool (exact match, fails loudly), or for a change across "
    "many files write a script with the Write tool into the scratchpad and run it with `python -I <file>` "
    '(pass newline="\\n"). See CLAUDE.md → Build & run → Edits.'
)

HEREDOC = re.compile(r"<<-?\s*['\"]?(\w+)['\"]?")
PYTHON = r"\bpython3?(?:\.exe)?\b"
WRITE_CALL = re.compile(r"write_text|write_bytes|open\([^,)]*,\s*(?:mode\s*=\s*)?['\"][^'\"]*[wax]")


def strip_heredoc_bodies(command: str) -> str:
    """The command lines only: each heredoc's body (up to its terminator) is dropped."""
    out, terminator = [], None
    for line in command.split("\n"):
        if terminator is not None:
            if line.strip() == terminator:
                terminator = None
            continue
        out.append(line)
        found = HEREDOC.findall(line)
        if found:
            terminator = found[-1]
    return "\n".join(out)


def strip_quotes(text: str) -> str:
    return re.sub(r"'[^']*'|\"(?:[^\"\\]|\\.)*\"", "''", text)


def blocked(command: str) -> bool:
    lines = strip_heredoc_bodies(command)
    bare = strip_quotes(lines)  # what the shell runs, without quoted text
    if re.search(r"\bsed\b[^|;&\n]*\s(?:-i|--in-place)", bare):
        return True
    for match in re.finditer(PYTHON + r"[^|;&\n]*\s-c\s+('[^']*'|\"(?:[^\"\\]|\\.)*\")([^|;&\n]*)", lines):
        code, rest = match.group(1), match.group(2)
        if WRITE_CALL.search(code) or re.search(r"(?<![0-9&])>{1,2}\s*[^\s&]", rest):
            return True
    if HEREDOC.search(lines):  # `lines`: the quotes around a terminator (<<'EOF') are gone from `bare`
        if re.search(PYTHON + r"[^\n]*<<", bare):
            return True
        if re.search(r"\btee\b|>{1,2}\s*[^\s&|]", bare.replace("2>&1", "").replace(">/dev/null", "")):
            return True
    return False


def main() -> None:
    try:
        event = json.load(sys.stdin)
    except ValueError:
        return
    command = (event.get("tool_input") or {}).get("command") or ""
    if blocked(command):
        print(MESSAGE, file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
