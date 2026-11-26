"""Token use of each subagent in a Claude Code session, without printing any content.

Usage: python -I scripts/agent-tokens.py [session-dir]
With no argument it reads this project's most recently changed session under ~/.claude/projects/.
Per subagent: its description, model, starting context (before any work), final context, tool output by tool
(chars and ~tokens at 4 chars/token) and images read. Used at cleanup (orchestrate skill → Cleanup).
"""
import collections
import json
import os
import sys
from pathlib import Path


def project_dir() -> Path:
    # Claude Code names a project's folder after its path, with every non-alphanumeric character as "-".
    slug = "".join(c if c.isalnum() else "-" for c in str(Path.cwd()))
    return Path.home() / ".claude" / "projects" / slug


def latest_session(root: Path) -> Path:
    sessions = [d for d in root.iterdir() if (d / "subagents").is_dir()]
    if not sessions:
        sys.exit(f"no session with subagents under {root}")
    return max(sessions, key=lambda d: max((f.stat().st_mtime for f in (d / "subagents").iterdir()), default=0))


def summarise(path: Path) -> dict:
    names, by_tool, calls = {}, collections.Counter(), collections.Counter()
    first = last = None
    images = 0
    for line in path.open(encoding="utf-8"):
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        msg = entry.get("message") or {}
        usage = msg.get("usage")
        if usage:
            total = sum(usage.get(k, 0) for k in ("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens"))
            first = total if first is None else first
            last = total
        content = msg.get("content")
        if not isinstance(content, list):
            continue
        for block in content:
            if block.get("type") == "tool_use":
                names[block["id"]] = block["name"]
                calls[block["name"]] += 1
            elif block.get("type") == "tool_result":
                tool = names.get(block.get("tool_use_id"), "?")
                body = block.get("content")
                if isinstance(body, str):
                    by_tool[tool] += len(body)
                elif isinstance(body, list):
                    for part in body:
                        if part.get("type") == "text":
                            by_tool[tool] += len(part.get("text", ""))
                        elif part.get("type") == "image":
                            images += 1
    return {"first": first, "last": last, "images": images, "by_tool": by_tool, "calls": calls}


def main() -> None:
    session = Path(sys.argv[1]) if len(sys.argv) > 1 else latest_session(project_dir())
    print(f"session: {session.name}")
    for transcript in sorted((session / "subagents").glob("agent-*.jsonl"), key=os.path.getmtime):
        meta_file = transcript.with_suffix(".meta.json")
        meta = json.loads(meta_file.read_text(encoding="utf-8")) if meta_file.exists() else {}
        s = summarise(transcript)
        print(f"\n{meta.get('description', transcript.stem)} [{meta.get('agentType', '?')}, {meta.get('model', '?')}]")
        print(f"  start {s['first']} tok, final {s['last']} tok, images read {s['images']}")
        for tool, chars in s["by_tool"].most_common():
            print(f"  {tool:16s} {chars:8d} chars  ~{chars // 4:6d} tok  calls={s['calls'][tool]}")


if __name__ == "__main__":
    main()
