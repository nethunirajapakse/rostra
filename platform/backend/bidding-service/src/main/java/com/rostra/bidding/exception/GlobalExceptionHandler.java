package com.rostra.bidding.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            Map<String, String> fieldErrors
    ) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(new ErrorResponse(
                Instant.now(), 400, "Bad Request", "Validation failed", fieldErrors
        ));
    }

    @ExceptionHandler(InvalidBidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBid(InvalidBidException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                Instant.now(), 400, "Bad Request", ex.getMessage(), null
        ));
    }

    @ExceptionHandler(AuctionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AuctionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                Instant.now(), 404, "Not Found", ex.getMessage(), null
        ));
    }

    @ExceptionHandler(AuctionNotBiddableException.class)
    public ResponseEntity<ErrorResponse> handleNotBiddable(AuctionNotBiddableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                Instant.now(), 409, "Conflict", ex.getMessage(), null
        ));
    }

    @ExceptionHandler(AuctionServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(AuctionServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(
                Instant.now(), 503, "Service Unavailable", ex.getMessage(), null
        ));
    }
}
