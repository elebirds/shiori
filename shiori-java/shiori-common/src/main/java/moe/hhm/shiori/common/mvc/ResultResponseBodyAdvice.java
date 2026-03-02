package moe.hhm.shiori.common.mvc;

import moe.hhm.shiori.common.api.Result;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestControllerAdvice
public class ResultResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public ResultResponseBodyAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (hasSkipWrapAnnotation(returnType)) {
            return false;
        }

        Class<?> parameterType = returnType.getParameterType();
        if (Result.class.isAssignableFrom(parameterType)) {
            return false;
        }

        if (HttpEntity.class.isAssignableFrom(parameterType)) {
            return false;
        }

        return !isRawResponseType(parameterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return Result.success();
        }

        if (body instanceof Result<?> || body instanceof HttpEntity<?> || isRawResponseType(body.getClass())) {
            return body;
        }

        if (selectedContentType != null && MediaType.TEXT_EVENT_STREAM.includes(selectedContentType)) {
            return body;
        }

        Result<Object> result = Result.success(body);
        if (body instanceof CharSequence && StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(result);
            } catch (JacksonException e) {
                return "{\"code\":0,\"message\":\"成功\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
            }
        }

        return result;
    }

    private boolean hasSkipWrapAnnotation(MethodParameter returnType) {
        if (returnType.hasMethodAnnotation(SkipResultWrap.class)) {
            return true;
        }

        Class<?> containingClass = returnType.getContainingClass();
        return AnnotatedElementUtils.hasAnnotation(containingClass, SkipResultWrap.class);
    }

    private boolean isRawResponseType(Class<?> type) {
        return Resource.class.isAssignableFrom(type)
                || byte[].class == type
                || StreamingResponseBody.class.isAssignableFrom(type)
                || ResponseBodyEmitter.class.isAssignableFrom(type)
                || SseEmitter.class.isAssignableFrom(type);
    }
}
