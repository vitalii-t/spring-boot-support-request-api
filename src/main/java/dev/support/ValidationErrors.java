package dev.support;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ValidationErrors {
  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String,String> invalid() {
    // Avoid logging rejected support messages or email addresses in validation errors.
    return Map.of("error", "Check the field formats and lengths in /openapi.json.");
  }
}
