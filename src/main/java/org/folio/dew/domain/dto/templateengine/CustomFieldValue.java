package org.folio.dew.domain.dto.templateengine;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomFieldValue {

  private String id;    // stored option-id (e.g. opt_1); null/omitted for repeatable text
  private String value; // resolved option label, or the raw text for repeatable text fields
}
