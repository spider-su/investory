package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankApi;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankImportResult;
import java.io.IOException;
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
    String filename = RyczaltUploadSupport.filename(file.getOriginalFilename(), "bank.csv");
    try {
      byte[] content = file.getBytes();
      RyczaltUploadSupport.requireBank(filename, file.getContentType(), content);
      return importBank(profileId, filename, file.getContentType(), content, authentication);
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Could not read bank upload", exception);
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
    String safeFilename = RyczaltUploadSupport.filename(filename, "bank.csv");
    RyczaltUploadSupport.requireBank(safeFilename, contentType, content);
    return bank.importBank(profileId, content, safeFilename, contentType);
  }
}
