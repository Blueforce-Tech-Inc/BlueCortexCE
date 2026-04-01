/**
 * Claude-Mem OpenClaw Plugin for Java Backend
 *
 * This plugin integrates Claude-Mem memory system with OpenClaw Gateway,
 * connecting to the Java Spring Boot backend instead of the TypeScript version.
 *
 * Architecture (v2 - based on TS version investigation):
 * ```
 * OpenClaw Gateway
 * └── Claude-Mem Java Plugin (this)
 *     ├── HTTP Client → Java Backend (localhost:37777)
 *     ├── before_prompt_build → appendSystemContext (context injection)
 *     └── Observation Recording (tool_result_persist)
 * ```
 *
 * Key Improvement (v2):
 * - Uses `appendSystemContext` via `before_prompt_build` hook instead of MEMORY.md file sync
 * - This matches TS version behavior and eliminates file write race conditions
 * - MEMORY.md is now managed solely by the Agent (not by this plugin)
 */
interface PluginLogger {
    debug?: (message: string) => void;
    info: (message: string) => void;
    warn: (message: string) => void;
    error: (message: string) => void;
}
interface PluginServiceContext {
    config: Record<string, unknown>;
    workspaceDir?: string;
    stateDir: string;
    logger: PluginLogger;
}
interface PluginCommandContext {
    senderId?: string;
    channel: string;
    isAuthorizedSender: boolean;
    args?: string;
    commandBody: string;
    config: Record<string, unknown>;
}
type PluginCommandResult = string | {
    text: string;
} | {
    text: string;
    format?: string;
};
interface BeforeAgentStartEvent {
    prompt?: string;
}
interface BeforePromptBuildEvent {
    prompt: string;
    messages: unknown[];
}
interface BeforePromptBuildResult {
    systemPrompt?: string;
    prependContext?: string;
    prependSystemContext?: string;
    appendSystemContext?: string;
}
interface ToolResultPersistEvent {
    toolName?: string;
    params?: Record<string, unknown>;
    message?: {
        content?: Array<{
            type: string;
            text?: string;
        }>;
    };
}
interface AgentEndEvent {
    messages?: Array<{
        role: string;
        content: string | Array<{
            type: string;
            text?: string;
        }>;
    }>;
}
interface SessionStartEvent {
    sessionId: string;
    resumedFrom?: string;
}
interface AfterCompactionEvent {
    messageCount: number;
    tokenCount?: number;
    compactedCount: number;
}
interface SessionEndEvent {
    sessionId: string;
    messageCount: number;
    durationMs?: number;
}
interface EventContext {
    sessionKey?: string;
    workspaceDir?: string;
    agentId?: string;
}
type EventCallback<T> = (event: T, ctx: EventContext) => void | Promise<void>;
interface OpenClawPluginApi {
    id: string;
    name: string;
    version?: string;
    source: string;
    config: Record<string, unknown>;
    pluginConfig?: Record<string, unknown>;
    logger: PluginLogger;
    registerService: (service: {
        id: string;
        start: (ctx: PluginServiceContext) => void | Promise<void>;
        stop?: (ctx: PluginServiceContext) => void | Promise<void>;
    }) => void;
    registerCommand: (command: {
        name: string;
        description: string;
        acceptsArgs?: boolean;
        requireAuth?: boolean;
        handler: (ctx: PluginCommandContext) => PluginCommandResult | Promise<PluginCommandResult>;
    }) => void;
    on: ((event: "before_agent_start", callback: EventCallback<BeforeAgentStartEvent>) => void) & ((event: "before_prompt_build", callback: (event: BeforePromptBuildEvent, ctx: EventContext) => BeforePromptBuildResult | Promise<BeforePromptBuildResult | void> | void) => void) & ((event: "tool_result_persist", callback: EventCallback<ToolResultPersistEvent>) => void) & ((event: "agent_end", callback: EventCallback<AgentEndEvent>) => void) & ((event: "session_start", callback: EventCallback<SessionStartEvent>) => void) & ((event: "session_end", callback: EventCallback<SessionEndEvent>) => void) & ((event: "after_compaction", callback: EventCallback<AfterCompactionEvent>) => void) & ((event: "gateway_start", callback: EventCallback<Record<string, never>>) => void);
    runtime: {
        channel: Record<string, Record<string, (...args: any[]) => Promise<any>>>;
    };
}
export default function claudeMemJavaPlugin(api: OpenClawPluginApi): void;
export {};
//# sourceMappingURL=index.d.ts.map