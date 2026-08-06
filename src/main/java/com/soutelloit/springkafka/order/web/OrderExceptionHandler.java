package com.soutelloit.springkafka.order.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.soutelloit.springkafka.order.exception.DuplicateRequestInFlightException;
import com.soutelloit.springkafka.order.exception.InsufficientStockException;
import com.soutelloit.springkafka.order.exception.LockTimeoutException;
import com.soutelloit.springkafka.order.exception.OrderNotFoundException;
import com.soutelloit.springkafka.order.exception.ProductNotFoundException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain failures onto RFC 9457 problem details. */
@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail onOrderNotFound(OrderNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", e.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail onProductNotFound(ProductNotFoundException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown product", e.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail onInsufficientStock(InsufficientStockException e) {
        return problem(HttpStatus.CONFLICT, "Insufficient stock", e.getMessage());
    }

    @ExceptionHandler(DuplicateRequestInFlightException.class)
    public ProblemDetail onDuplicateInFlight(DuplicateRequestInFlightException e) {
        return problem(HttpStatus.CONFLICT, "Duplicate request in flight", e.getMessage());
    }

    /**
     * Either the in-JVM ReentrantLock or the PostgreSQL row lock timed out.
     * 503 rather than 500: the request is retryable and nothing was written.
     */
    @ExceptionHandler({
            LockTimeoutException.class,
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class})
    public ProblemDetail onLockTimeout(Exception e) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE,
                "Could not acquire lock", e.getMessage());
        problem.setProperty("retryable", true);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidationFailure(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST,
                "Validation failed", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
