package org.folio.dew.domain.dto.templateengine;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomFieldContext {

  private String name;                   // display name from definition.name
  private String type;                   // definition type, e.g. SINGLE_SELECT_DROPDOWN / SINGLE_CHECKBOX / TEXTBOX_LONG
  private Object value;         // scalar: CustomFieldOptionValue {id,label} (single-select) | Boolean (checkbox) | String (textbox/date/number)
  private List<Object> values; // multi-select → CustomFieldOptionValue {id,label}; repeatable text → String
}
