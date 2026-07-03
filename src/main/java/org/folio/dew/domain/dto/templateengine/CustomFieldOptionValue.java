package org.folio.dew.domain.dto.templateengine;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomFieldOptionValue {

  private String id;    // stored option-id (e.g. opt_1) - selects only
  private String label; // resolved option label
}
