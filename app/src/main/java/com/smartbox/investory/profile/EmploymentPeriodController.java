package com.smartbox.investory.profile;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.profile.api.model.EmploymentPeriod;
import com.smartbox.investory.profile.api.model.EmploymentType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portfolios/{profileId}/profile/employment-periods")
public class EmploymentPeriodController {
  private final EmploymentPeriodRepository repository;
  private final AuthorizationService authorization;

  public EmploymentPeriodController(
      EmploymentPeriodRepository repository, AuthorizationService authorization) {
    this.repository = repository;
    this.authorization = authorization;
  }

  @GetMapping
  public List<EmploymentPeriod> list(@PathVariable long profileId, Authentication authentication) {
    checkRead(profileId, authentication);
    return repository.findAll(profileId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EmploymentPeriod create(
      @PathVariable long profileId, @RequestBody Request request, Authentication authentication) {
    checkWrite(profileId, authentication);
    return repository.save(profileId, request.toPeriod(null));
  }

  @PutMapping("/{id}")
  public EmploymentPeriod update(
      @PathVariable long profileId,
      @PathVariable long id,
      @RequestBody Request request,
      Authentication authentication) {
    checkWrite(profileId, authentication);
    return repository.save(profileId, request.toPeriod(id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable long profileId, @PathVariable long id, Authentication authentication) {
    checkWrite(profileId, authentication);
    repository.delete(profileId, id);
  }

  private void checkRead(long profileId, Authentication authentication) {
    if (!authorization.canRead(profileId, authentication))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  private void checkWrite(long profileId, Authentication authentication) {
    if (!authorization.canWrite(profileId, authentication))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  }

  public record Request(
      EmploymentType type,
      LocalDate from,
      LocalDate to,
      Boolean qualifiesAsPrimarySocialInsuranceTitle) {
    EmploymentPeriod toPeriod(Long id) {
      return new EmploymentPeriod(
          id, type, from, to, Boolean.TRUE.equals(qualifiesAsPrimarySocialInsuranceTitle));
    }
  }
}
