// Codex Event Types
// Based on transcript-watch.example.json schema for Codex session JSONL files.

// Raw JSONL event from Codex session file
export interface CodexJsonlEvent {
  type: string;
  payload: Record<string, unknown>;
}

// Action types derived from event processing
export type CodexAction =
  | "session_context"
  | "session_init"
  | "assistant_message"
  | "tool_use"
  | "tool_result"
  | "session_end";

// Parsed event with extracted fields
export interface ParsedCodexEvent {
  action: CodexAction;
  sessionId?: string;
  cwd?: string;
  toolName?: string;
  toolInput?: string;
  toolResponse?: string;
  toolId?: string;
  message?: string;
  prompt?: string;
}

// Event type to action mapping
export const CODEX_EVENT_ACTIONS: Record<string, CodexAction> = {
  session_meta: "session_context",
  turn_context: "session_context",
  user_message: "session_init",
  agent_message: "assistant_message",
  function_call: "tool_use",
  custom_tool_call: "tool_use",
  web_search_call: "tool_use",
  function_call_output: "tool_result",
  custom_tool_call_output: "tool_result",
  turn_aborted: "session_end",
};

// Parse a raw JSONL line into a ParsedCodexEvent
export function parseCodexEvent(raw: string): ParsedCodexEvent | null {
  try {
    const event: CodexJsonlEvent = JSON.parse(raw);
    const eventType = event.type;
    const payload = event.payload || {};

    const action = CODEX_EVENT_ACTIONS[eventType];
    if (!action) {
      return null;
    }

    const parsed: ParsedCodexEvent = { action };

    switch (action) {
      case "session_context":
        parsed.sessionId = (payload.id as string) || (payload.sessionId as string);
        parsed.cwd = (payload.cwd as string) || extractPath(payload);
        break;

      case "session_init":
        parsed.prompt = (payload.message as string) || (payload.prompt as string);
        parsed.cwd = (payload.cwd as string) || extractPath(payload);
        break;

      case "assistant_message":
        parsed.message = (payload.message as string) || "";
        break;

      case "tool_use":
        parsed.toolId = (payload.call_id as string) || (payload.toolId as string);
        parsed.toolName =
          (payload.name as string) ||
          (payload.toolName as string) ||
          (payload.tool_name as string) ||
          "unknown";
        parsed.toolInput = stringifyToolInput(
          (payload.arguments as string) ||
            (payload.input as Record<string, unknown>) ||
            (payload.action as Record<string, unknown>) ||
            {}
        );
        break;

      case "tool_result":
        parsed.toolId = (payload.call_id as string) || (payload.toolId as string);
        parsed.toolResponse = (payload.output as string) || "";
        break;

      case "session_end":
        break;
    }

    return parsed;
  } catch {
    return null;
  }
}

function extractPath(payload: Record<string, unknown>): string | undefined {
  if (typeof payload.cwd === "string") return payload.cwd;
  if (typeof payload.path === "string") return payload.path;
  if (typeof payload.uri === "string") return payload.uri;
  return undefined;
}

function stringifyToolInput(input: unknown): string {
  if (typeof input === "string") return input;
  if (input === null || input === undefined) return "";
  try {
    return JSON.stringify(input);
  } catch {
    return String(input);
  }
}
