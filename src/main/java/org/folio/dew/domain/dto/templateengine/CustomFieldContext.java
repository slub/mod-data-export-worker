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
  private Object value;                  // scalar: String | Boolean (single-select/radio/checkbox/textbox/date/number)
  private List<CustomFieldValue> values; // array: multi-select / repeatable
}
