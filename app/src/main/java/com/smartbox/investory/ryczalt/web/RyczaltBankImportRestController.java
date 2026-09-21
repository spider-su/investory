package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankApi;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profiles/{profileId}/accounting/bank")
public class RyczaltBankImportRestController {
  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

  private final RyczaltBankApi bank;
  private final AuthorizationService authorization;

  public RyczaltBankImportRestController(RyczaltBankApi bank, AuthorizationService authorization) {
    this.bank = bank;
    this.authorization = authorization;
  }

  @PostMapping("/import")
  public RyczaltBankImportResult importBank(
      @PathVariable long profileId,
      @RequestPart("file") MultipartFile file,
      Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty bank CSV is required");
    try {
      return importBank(
          profileId,
          file.getOriginalFilename(),
          file.getContentType(),
          file.getBytes(),
          authentication);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank import failed", exception);
    }
  }

  public RyczaltBankImportResult importBank(
      long profileId,
      String filename,
      String contentType,
      byte[] content,
      Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (content == null || content.length == 0 || content.length > MAX_FILE_SIZE)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty bank CSV is required");
    return bank.importBank(profileId, content, filename, contentType);
  }
}
