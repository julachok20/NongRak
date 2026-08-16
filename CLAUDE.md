# NongRak AI

## Read these first

| File | What it is |
|------|------------|
| **`AGENT_GUIDE.md`** | **Standing rules for agents** — conventions that cannot be broken, workflow, traps. Read before changing anything. |
| `MEMORY_GRAPH.md` | Running project log. Newest batch at the bottom. |
| `AGENT_HANDOFF.md` | Latest session: what changed, what is open, what was already rejected and why. |
| `docs/MODULE_INDEX.md` | Module map. |

Four rules worth repeating here, because breaking them is expensive:

1. **Money is integer satang** (THB × 100), never a float.
2. **Authorisation lives in Postgres** — RLS and `security definer` RPCs, not the client. Read the
   RPC body before assuming something is impossible; several "missing features" turned out to be
   already permitted server-side with no button.
3. **No silent failure.** Errors must name what was *received*, not only what was wanted. This is
   the project's most expensive recurring bug class.
4. **Read existing code before adding any.** Search the domain word as well as the mechanism — a
   duplicate animation engine was once built because the existing one was named differently.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
