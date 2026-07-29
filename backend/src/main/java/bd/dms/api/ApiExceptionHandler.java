package bd.dms.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the failures that reach a controller to clean HTTP responses. Bad credentials on the
 * login/refresh path surface as 401 rather than the default 500; malformed request bodies as 400.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String error) {}

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> onAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("invalid_credentials"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> onValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request"));
    }

    /** An out-of-range argument (e.g. an unsupported simulation speed) is a bad request, not a 500. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> onIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request"));
    }

    /** A duplicate unique key (e.g. an already-used disaster/camp code) is a bad request, not a
     * 500 — matters most on the proposal path, where without this a duplicate-code proposal
     * would 500 on every approval attempt and could never be cleanly resolved. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> onDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("duplicate_or_invalid_data"));
    }
}
