package moe.hhm.shiori.common.mvc;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import moe.hhm.shiori.common.api.Result;
import moe.hhm.shiori.common.error.CommonErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.exception.ValidationErrorItem;
import moe.hhm.shiori.common.exception.ValidationErrorPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String SECURITY_AUTHENTICATION_EXCEPTION =
            "org.springframework.security.core.AuthenticationException";
    private static final String SECURITY_ACCESS_DENIED_EXCEPTION =
            "org.springframework.security.access.AccessDeniedException";

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Object>> handleBizException(BizException ex) {
        return build(ex.getHttpStatus(), ex.getErrorCode().code(), ex.getErrorCode().message(), ex.getExtraData());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        return validationBadRequest(ex.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Object>> handleBindException(BindException ex) {
        return validationBadRequest(ex.getFieldErrors());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        List<ValidationErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new ValidationErrorItem(v.getPropertyPath().toString(), v.getMessage(), v.getInvalidValue()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_PARAM.code(), CommonErrorCode.INVALID_PARAM.message(),
                new ValidationErrorPayload(errors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Object>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        ValidationErrorItem error = new ValidationErrorItem(ex.getParameterName(), ex.getMessage(), null);
        return build(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_PARAM.code(), CommonErrorCode.INVALID_PARAM.message(),
                new ValidationErrorPayload(List.of(error)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        ValidationErrorItem error = new ValidationErrorItem(ex.getName(), "参数类型不匹配", ex.getValue());
        return build(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_PARAM.code(), CommonErrorCode.INVALID_PARAM.message(),
                new ValidationErrorPayload(List.of(error)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, CommonErrorCode.BAD_REQUEST.code(), CommonErrorCode.BAD_REQUEST.message(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Object>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED.code(),
                CommonErrorCode.METHOD_NOT_ALLOWED.message(), null);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Object>> handleThrowable(Throwable ex) {
        if (hasCauseOfType(ex, SECURITY_AUTHENTICATION_EXCEPTION)) {
            return build(HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED.code(), CommonErrorCode.UNAUTHORIZED.message(),
                    null);
        }

        if (hasCauseOfType(ex, SECURITY_ACCESS_DENIED_EXCEPTION)) {
            return build(HttpStatus.FORBIDDEN, CommonErrorCode.FORBIDDEN.code(), CommonErrorCode.FORBIDDEN.message(), null);
        }

        log.error("未捕获异常", ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_SERVER_ERROR.code(),
            CommonErrorCode.INTERNAL_SERVER_ERROR.message(), buildInternalErrorDetail(ex));
    }

    private ResponseEntity<Result<Object>> validationBadRequest(List<FieldError> fieldErrors) {
        List<ValidationErrorItem> errors = new ArrayList<>(fieldErrors.size());
        for (FieldError fieldError : fieldErrors) {
            errors.add(new ValidationErrorItem(fieldError.getField(), fieldError.getDefaultMessage(),
                    fieldError.getRejectedValue()));
        }
        return build(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_PARAM.code(), CommonErrorCode.INVALID_PARAM.message(),
                new ValidationErrorPayload(errors));
    }

    private ResponseEntity<Result<Object>> build(HttpStatus status, int code, String message, Object data) {
        return ResponseEntity.status(status).body(Result.failure(code, message, data));
    }

    private boolean hasCauseOfType(Throwable ex, String typeName) {
        Class<?> exceptionType = resolveClass(typeName);
        if (exceptionType == null) {
            return false;
        }

        Throwable current = ex;
        while (current != null) {
            if (exceptionType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Class<?> resolveClass(String className) {
        try {
            return Class.forName(className, false, GlobalExceptionHandler.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private String buildInternalErrorDetail(Throwable ex) {
        if (ex == null) {
            return "unknown";
        }
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }
}
