package com.ablueforce.cortexce.service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * One retry classification shared by every queue worker.
 *
 * <p>Two code paths used to decide this independently: {@link AgentService} classified
 * errors inside the job, and {@code PendingMessageEventListener} treated every escaped
 * exception as terminal. That made the retry budget depend on <i>where</i> an error
 * surfaced rather than on what the error was, so a transient failure in the listener
 * path was dead-lettered immediately. Keeping the rule in one place makes both paths
 * use the same budget.
 */
public final class RetryPolicy {

    private RetryPolicy() {
    }

    /**
     * @return true when the failure is transient (429/5xx/timeout/connect), false when
     *         retrying cannot succeed (bad credentials, denied permission, unusable answer)
     */
    public static boolean isRetryable(Throwable error) {
        if (error == null) {
            return false;
        }
        // A classified provider failure carries the decision: only transient kinds
        // (429 / timeout / 5xx) are retried; auth and unusable answers are terminal.
        if (error instanceof LlmService.ProviderFailureException provider) {
            return provider.kind().isTransient();
        }
        String message = error.getMessage();
        // Unrecoverable auth errors - do NOT retry (prevents 77K+ retry loops)
        if (message != null && (message.contains("API_KEY_INVALID")
            || message.contains("API key expired")
            || message.contains("API key not valid")
            || message.contains("PERMISSION_DENIED")
            || message.contains("Gemini API error: 400")
            || message.contains("Gemini API error: 401")
            || message.contains("Gemini API error: 403"))) {
            return false;
        }
        if (error instanceof SocketTimeoutException
            || error instanceof HttpTimeoutException
            || error instanceof ConnectException
            || error instanceof TimeoutException) {
            return true;
        }
        // LLM service errors are retryable
        return message != null && message.contains("LLM");
    }

    /**
     * Short, non-sensitive classification stored on the queue row when a job dies.
     * Message text is never used: it can contain prompts, tool output or credentials.
     */
    public static String failureKind(Throwable error) {
        if (error == null) {
            return "UNKNOWN";
        }
        if (error instanceof LlmService.ProviderFailureException provider) {
            return provider.kind().name();
        }
        if (error instanceof SocketTimeoutException || error instanceof HttpTimeoutException
            || error instanceof TimeoutException) {
            return "TIMEOUT";
        }
        if (error instanceof ConnectException) {
            return "CONNECT";
        }
        String simpleName = error.getClass().getSimpleName();
        if (simpleName.contains("DataValidation") || simpleName.contains("Parse")) {
            return "VALIDATION";
        }
        return "UNKNOWN";
    }
}
