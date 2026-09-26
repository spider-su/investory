package com.smartbox.investory.ui.accounting;

import jakarta.servlet.http.HttpServletRequest;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** MVC routes for document recognition and candidate approval. */
@Controller
@RequestMapping("/profiles/{profileId}/accounting/documents")
public class RyczaltAccountingDocumentController {
  private final RyczaltWebAccountingClient client;

  public RyczaltAccountingDocumentController(RyczaltWebAccountingClient client) {
    this.client = client;
  }

  @PostMapping("/recognize")
  public String recognize(
      @PathVariable long profileId, @RequestParam MultipartFile file, RedirectAttributes redirect) {
    try {
      var candidate =
          client.recognize(
              profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
      if ("APPROVED".equals(candidate.approvalStatus())) {
        redirect.addFlashAttribute(
            "accountingMessage", "Document recognized and approved automatically.");
        return "redirect:/profiles/" + profileId + "/accounting";
      }
      redirect.addFlashAttribute("accountingMessage", "Document recognized. Review the candidate.");
      return "redirect:/profiles/"
          + profileId
          + "/accounting/documents/candidates/"
          + candidate.candidateKey();
    } catch (Exception exception) {
      RyczaltAccountingWebSupport.logFailure("document-recognition", profileId, null, exception);
      redirect.addFlashAttribute(
          "accountingError",
          RyczaltAccountingWebSupport.userMessage(exception, "Document recognition failed."));
      return "redirect:/profiles/" + profileId + "/accounting";
    }
  }

  @GetMapping("/candidates/{candidateKey}")
  public String candidate(
      @PathVariable long profileId,
      @PathVariable UUID candidateKey,
      Model model,
      HttpServletRequest request) {
    var candidate = client.candidate(profileId, candidateKey);
    var previousMonth =
        YearMonth.of(candidate.periodYear(), candidate.periodMonth()).minusMonths(1);
    var previousInvoices =
        client.periods(profileId).stream().anyMatch(item -> item.month().equals(previousMonth))
            ? client.invoices(profileId, previousMonth)
            : List.<RyczaltWebAccountingClient.Invoice>of();
    model.addAttribute("profileId", profileId);
    model.addAttribute("candidate", candidate);
    model.addAttribute("counterparties", client.counterparties(profileId));
    model.addAttribute("previousMonth", previousMonth);
    model.addAttribute("previousInvoices", previousInvoices);
    model.addAttribute("canWrite", RyczaltAccountingWebSupport.canWrite(request));
    return "accounting/ryczalt-candidate";
  }

  @PostMapping("/candidates/{candidateKey}")
  public String approveCandidate(
      @PathVariable long profileId,
      @PathVariable UUID candidateKey,
      @RequestParam(required = false) Long counterpartyId,
      @RequestParam(required = false) String classification,
      @RequestParam(required = false) String vatTreatment,
      @RequestParam(required = false) String vatDeductionRatio,
      @RequestParam(required = false) String ryczaltRate,
      @RequestParam(required = false) String paymentVerificationPolicy,
      @RequestParam(defaultValue = "false") boolean approve,
      @RequestParam(defaultValue = "false") boolean rememberRule,
      @RequestParam(required = false) String ruleName,
      @RequestParam(required = false) String serviceKey,
      RedirectAttributes redirect) {
    try {
      client.approveCandidate(
          profileId,
          candidateKey,
          counterpartyId,
          classification,
          vatTreatment,
          vatDeductionRatio,
          ryczaltRate,
          paymentVerificationPolicy,
          approve,
          rememberRule,
          ruleName,
          serviceKey);
      redirect.addFlashAttribute(
          "accountingMessage", approve ? "Invoice approved." : "Candidate saved for review.");
    } catch (Exception exception) {
      RyczaltAccountingWebSupport.logFailure("candidate-approval", profileId, null, exception);
      redirect.addFlashAttribute(
          "accountingError",
          RyczaltAccountingWebSupport.userMessage(exception, "Candidate could not be saved."));
      return "redirect:/profiles/" + profileId + "/accounting/documents/candidates/" + candidateKey;
    }
    return "redirect:/profiles/" + profileId + "/accounting";
  }
}
