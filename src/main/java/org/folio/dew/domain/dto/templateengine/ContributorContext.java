package org.folio.dew.domain.dto.templateengine;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContributorContext {

  private String contributor;
  private String contributorNameType;
  private String contributorNameTypeName;
}