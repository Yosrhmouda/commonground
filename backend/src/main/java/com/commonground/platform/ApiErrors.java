package com.commonground.platform;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> appError(ApiException ex) { return ResponseEntity.status(ex.status()).body(Map.of("error", ex.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        var first = ex.getBindingResult().getFieldErrors().stream().findFirst();
        return ResponseEntity.badRequest().body(Map.of("error", first.map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("Please check your entries.")));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> uploadSize() { return ResponseEntity.status(413).body(Map.of("error", "Files must total 50 MB or less.")); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<?> malformed(Exception ex) { return ResponseEntity.badRequest().body(Map.of("error", "Please complete all required fields and choose at least one file.")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex) {
        LoggerFactory.getLogger(getClass()).error("Request failed", ex);
        return ResponseEntity.internalServerError().body(Map.of("error", "We couldn't complete this request. Please try again."));
    }
}
