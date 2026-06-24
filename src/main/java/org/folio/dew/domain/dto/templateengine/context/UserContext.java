package org.folio.dew.domain.dto.templateengine.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserContext {

  private String id;
  private String username;
  private String firstName;
  private String lastName;
  private String middleName;
  private String preferredFirstName;
  private String email;
  private String fullName;
}