package org.folio.dew.domain.dto.acquisitions.customfields;

import lombok.Data;

@Data
public class CustomField {
  private String refId;
  private String name;
  private String type;
  private Boolean visible;
  private SelectField selectField;
}
