package com.icentric.Icentric.config;

import com.icentric.Icentric.identity.exception.UserNotFoundException;
import com.icentric.Icentric.learning.exception.CertificateGenerationException;
import com.icentric.Icentric.learning.exception.SequentialLockException;
import com.icentric.Icentric.platform.exception.TenantNotFoundException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Translates application exceptions into RFC-7807 ProblemDetail responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Spring Security bad credentials (login failures) */
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.UNAUTHORIZED,
                "Authentication failed",
                ex.getMessage(),
                request
        );
    }

    /** Missing/invalid authentication context on protected endpoints */
    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    ProblemDetail handleMissingAuth(AuthenticationCredentialsNotFoundException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                ex.getMessage(),
                request
        );
    }

    /** Invalid request payload or business input */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                ex.getMessage(),
                request
        );
    }

    /** Business rule violations; keep known conflicts as 409 */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        String detail = ex.getMessage() == null ? "Illegal state" : ex.getMessage();
        HttpStatus status = detail.toLowerCase().contains("already exists")
                ? HttpStatus.CONFLICT
                : HttpStatus.INTERNAL_SERVER_ERROR;
        String title = status == HttpStatus.CONFLICT ? "Request conflict" : "Illegal application state";

        return buildProblem(status, title, detail, request);
    }

    /** Bad or expired JWT */
    @ExceptionHandler(JwtException.class)
    ProblemDetail handleJwtException(JwtException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.UNAUTHORIZED,
                "Invalid token",
                ex.getMessage(),
                request
        );
    }

    /** Authorization failures */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ProblemDetail handleAccessDenied(Exception ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.FORBIDDEN,
                "Access denied",
                ex.getMessage(),
                request
        );
    }

    /** Bean Validation failures (@Valid) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                errors,
                request
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        String errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + defaultMessage(error)))
                .collect(Collectors.joining(", "));

        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                errors.isBlank() ? ex.getMessage() : errors,
                request
        );
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class
    })
    ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                ex.getMessage(),
                request
        );
    }

    /** User not found → 404 */
    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.NOT_FOUND,
                "User not found",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.CONFLICT,
                "Request conflict",
                ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Payload too large",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(CertificateGenerationException.class)
    ProblemDetail handleCertificateGeneration(CertificateGenerationException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Certificate generation failed",
                ex.getMessage(),
                request
        );
    }

    /** Sequential lesson lock – learner tries to skip ahead */
    @ExceptionHandler(SequentialLockException.class)
    ProblemDetail handleSequentialLock(SequentialLockException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.FORBIDDEN,
                "Lesson locked",
                ex.getMessage(),
                request
        );
    }

    /** Tenant not found → 404 */
    @ExceptionHandler(TenantNotFoundException.class)
    ProblemDetail handleTenantNotFound(TenantNotFoundException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.NOT_FOUND,
                "Tenant not found",
                ex.getMessage(),
                request
        );
    }

    /** Catch-all */
    @ExceptionHandler(RuntimeException.class)
    ProblemDetail handleRuntime(RuntimeException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                ex.getMessage(),
                request
        );
    }

    private ProblemDetail buildProblem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setDetail(detail);
        if (request != null) {
            pd.setInstance(URI.create(request.getRequestURI()));
        }
        return pd;
    }

    private String defaultMessage(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "Validation error";
    }
}
