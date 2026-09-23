package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.RyczaltInvoiceApprovalService;
import com.smartbox.investory.ryczalt.application.RyczaltInvoiceApprovalService.ApproveCommand;
import com.smartbox.investory.ryczalt.application.RyczaltInvoiceRecognitionService;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profiles/{profileId}/accounting/invoices")
public class RyczaltInvoiceRecognitionRestController {
  private final AuthorizationService authorization;
  private final RyczaltInvoiceRecognitionService recognition;
  private final RyczaltInvoiceApprovalService approval;

  public RyczaltInvoiceRecognitionRestController(
      AuthorizationService authorization,
      RyczaltInvoiceRecognitionService recognition,
      RyczaltInvoiceApprovalService approval) {
    this.authorization = authorization;
    this.recognition = recognition;
    this.approval = approval;
  }

  @PostMapping("/recognize")
  public RyczaltInvoiceRecognitionService.CandidateView recognize(
      @PathVariable long profileId,
      @RequestPart("file") MultipartFile file,
      Authentication authentication) {
    write(profileId, authentication);
    String filename = RyczaltUploadSupport.filename(file.getOriginalFilename(), "invoice.pdf");
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Could not read invoice upload", exception);
    }
    RyczaltUploadSupport.requireInvoice(filename, file.getContentType(), content);
    return recognize(profileId, filename, file.getContentType(), content, authentication);
  }

  public RyczaltInvoiceRecognitionService.CandidateView recognize(
      long profileId,
      String filename,
      String contentType,
      byte[] content,
      Authentication authentication) {
    write(profileId, authentication);
    String safeFilename = RyczaltUploadSupport.filename(filename, "invoice.pdf");
    RyczaltUploadSupport.requireInvoice(safeFilename, contentType, content);
    return recognition.recognize(profileId, safeFilename, contentType, content);
  }

  @GetMapping("/candidates/{candidateKey}")
  public RyczaltInvoiceRecognitionService.CandidateView candidate(
      @PathVariable long profileId,
      @PathVariable UUID candidateKey,
      Authentication authentication) {
    read(profileId, authentication);
    return recognition.get(profileId, candidateKey);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RyczaltInvoiceApprovalService.InvoiceView approve(
      @PathVariable long profileId,
      @RequestBody ApprovalRequest request,
      Authentication authentication) {
    write(profileId, authentication);
    if (request.candidateKey() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidateKey is required");
    return approval.approve(profileId, request.candidateKey(), request.command());
  }

  private void read(long p, Authentication a) {
    if (!authorization.canRead(p, a)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  private void write(long p, Authentication a) {
    if (!authorization.canWrite(p, a)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  public record ApprovalRequest(
      UUID candidateKey,
      Long counterpartyId,
      String classification,
      String vatTreatment,
      String vatDeductionRatio,
      String ryczaltRate,
      PaymentVerificationPolicy paymentVerificationPolicy,
      boolean approve,
      boolean rememberRule,
      String ruleName,
      String serviceKey) {
    ApproveCommand command() {
      return new ApproveCommand(
          counterpartyId,
          classification,
          vatTreatment,
          decimal(vatDeductionRatio),
          decimal(ryczaltRate),
          paymentVerificationPolicy,
          approve,
          rememberRule,
          ruleName,
          serviceKey);
    }

    private static BigDecimal decimal(String value) {
      if (value == null || value.isBlank()) return null;
      try {
        return new BigDecimal(value.trim());
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("Invalid decimal value: " + value, exception);
      }
    }
  }
}
