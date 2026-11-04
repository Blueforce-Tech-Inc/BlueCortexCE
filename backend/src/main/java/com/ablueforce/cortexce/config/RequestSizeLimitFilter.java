package com.ablueforce.cortexce.config;

import com.ablueforce.cortexce.common.ErrorResponses;
import com.ablueforce.cortexce.common.PayloadTooLargeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Rejects request bodies larger than {@code cortexce.http.max-body-bytes} (2 MiB by default)
 * on {@code /api/**} with {@code 413} in the standard error envelope.
 *
 * <p>Tomcat has no body limit for a JSON request: {@code maxPostSize} applies to form
 * parameters only, so before this filter a 5 MB observation was parsed and stored whole
 * (measured in the H2 load note). Two paths are covered:
 *
 * <ul>
 *   <li>A declared {@code Content-Length} over the limit is refused before the body is
 *       read. Tomcat discards the unread remainder up to {@code server.tomcat.max-swallow-size}
 *       (raised to 8 MB), which keeps the 413 readable by the client instead of a reset.</li>
 *   <li>A chunked body has no length to check, so the stream is wrapped with a counter and
 *       the first byte past the limit raises {@link PayloadTooLargeException}; the global
 *       handler maps that to the same 413 body.</li>
 * </ul>
 *
 * <p>Requests without a body (GET/HEAD/OPTIONS) and non-API paths (the compiled Viewer
 * bundle, the MCP endpoints) are untouched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long DEFAULT_MAX_BODY_BYTES = 2L * 1024 * 1024;

    private final long maxBodyBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(
            @Value("${cortexce.http.max-body-bytes:" + DEFAULT_MAX_BODY_BYTES + "}") long maxBodyBytes,
            ObjectMapper objectMapper) {
        this.maxBodyBytes = maxBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxBodyBytes) {
            reject(response);
            return;
        }
        if (declared >= 0) {
            chain.doFilter(request, response);
            return;
        }
        // Chunked transfer: no length header, so count while the body is parsed.
        chain.doFilter(new LimitedRequest(request, maxBodyBytes), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorResponses.body(
            ErrorResponses.PAYLOAD_TOO_LARGE,
            "Request body exceeds " + maxBodyBytes + " bytes",
            Map.of("max_bytes", maxBodyBytes)));
    }

    /**
     * Wraps a chunked request so the parser sees {@link PayloadTooLargeException} as soon as
     * the byte count crosses the limit. {@link #getContentLength()} still reports -1, which
     * every consumer already handles.
     */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedInputStream(super.getInputStream(), limit);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long consumed;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        private void check(long added) throws PayloadTooLargeException {
            consumed += added;
            if (consumed > limit) {
                throw new PayloadTooLargeException(limit);
            }
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                check(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = delegate.read(bytes, offset, length);
            if (read > 0) {
                check(read);
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
