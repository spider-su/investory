package com.smartbox.investory.config;

import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi;
import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.web.AccountIdParser;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.shared.time.ApplicationTime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RestApiExceptionHandler {

  private final ApplicationTime applicationTime;

  public RestApiExceptionHandler(ApplicationTime applicationTime) {
    this.applicationTime = applicationTime;
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    ServletRequestBindingException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class,
    InvestmentImportApi.ImportFailure.class,
    InvestmentMaintenanceApi.InvalidMaintenanceRequest.class,
    AccountIdParser.InvalidAccountSelectionException.class,
    InvestmentDashboardApi.InvalidPortfolioRequest.class
  })
  public ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, message(exception), request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> responseStatus(
      ResponseStatusException exception, HttpServletRequest request) {
    var status = HttpStatus.resolve(exception.getStatusCode().value());
    if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
    return error(
        status, status.is4xxClientError() ? message(exception) : "Internal server error", request);
  }

  @ExceptionHandler({
    ResourceNotFoundException.class,
    InvestmentAssetApi.AssetNotFoundException.class,
    InvestmentDashboardApi.PortfolioNotFoundException.class,
    RetirementPlanApi.RevisionNotFoundException.class,
    RetirementPlanApi.EventNotFoundException.class
  })
  public ResponseEntity<ApiError> resourceNotFound(
      RuntimeException exception, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, message(exception), request);
  }

  @ExceptionHandler(RetirementPlanApi.PlanNotFoundException.class)
  public ResponseEntity<ApiError> planNotFound(
      RetirementPlanApi.PlanNotFoundException exception, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, message(exception), request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> staticResourceNotFound(
      NoResourceFoundException exception, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, message(exception), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> internalServerError(
      Exception exception, HttpServletRequest request) {
    log.error("Unhandled REST failure: path={}", request.getRequestURI(), exception);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
  }

  private ResponseEntity<ApiError> error(
      HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(
            new ApiError(status.value(), message, request.getRequestURI(), applicationTime.now()));
  }

  private static String message(Exception exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? "Bad request"
        : exception.getMessage();
  }

  public record ApiError(int status, String message, String path, Instant timestamp) {}
}
