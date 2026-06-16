package org.folio.dew.domain.dto.acquisitions.customfields;

import java.util.List;

import lombok.Data;

@Data
public class CustomFieldCollection {
  private List<CustomField> customFields;
}
