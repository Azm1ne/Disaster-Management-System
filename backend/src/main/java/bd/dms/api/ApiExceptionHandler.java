package bd.dms.api;

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
}
