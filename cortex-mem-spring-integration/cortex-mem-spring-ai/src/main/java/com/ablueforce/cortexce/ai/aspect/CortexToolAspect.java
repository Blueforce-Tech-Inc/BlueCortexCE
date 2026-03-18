package com.ablueforce.cortexce.ai.aspect;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.ai.observation.ObservationCaptureService;
import com.ablueforce.cortexce.client.dto.ObservationRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.Ordered;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AOP aspect that intercepts all {@code @Tool}-annotated methods and
 * automatically records the execution as an observation in the memory system.
 * <p>
 * Requires an active {@link CortexSessionContext} to identify the session
 * and project. If no context is active, the tool executes normally without capture.
 */
@Aspect
public class CortexToolAspect implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(CortexToolAspect.class);

    private final ObservationCaptureService captureService;

    public CortexToolAspect(ObservationCaptureService captureService) {
        this.captureService = captureService;
    }

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object interceptToolExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!CortexSessionContext.isActive()) {
            return joinPoint.proceed();
        }

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        String toolName = toolAnnotation.name().isEmpty()
            ? method.getName()
            : toolAnnotation.name();

        Map<String, Object> toolInput = buildInputMap(joinPoint.getArgs(), method.getParameters());

        Object result = joinPoint.proceed();

        try {
            captureService.recordToolObservation(ObservationRequest.builder()
                .sessionId(CortexSessionContext.getSessionId())
                .projectPath(CortexSessionContext.getProjectPath())
                .toolName(toolName)
                .toolInput(toolInput)
                .toolResponse(Map.of("result", result != null ? result.toString() : "null"))
                .promptNumber(CortexSessionContext.getPromptNumber())
                .build());
        } catch (Exception e) {
            log.warn("Failed to capture tool observation for {}: {}", toolName, e.getMessage());
        }

        return result;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE + 100;
    }

    private Map<String, Object> buildInputMap(Object[] args, Parameter[] params) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < params.length; i++) {
            map.put(params[i].getName(), args[i] != null ? args[i].toString() : "null");
        }
        return map;
    }
}
