package org.folio.dew.batch.acquisitions.services;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.dew.batch.acquisitions.utils.ExportUtils;
import org.folio.dew.domain.dto.acquisitions.customfields.CustomField;
import org.folio.dew.domain.dto.acquisitions.customfields.SelectField;
import org.folio.dew.domain.dto.acquisitions.customfields.SelectFieldOption;
import org.folio.dew.domain.dto.acquisitions.customfields.SelectFieldOptions;
import org.folio.dew.domain.dto.templateengine.CustomFieldContext;
import org.folio.dew.domain.dto.templateengine.CustomFieldOptionValue;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Turns the raw {@code refId -> value} custom-fields map carried on a purchase order or
 * PO line into template-ready substructures, resolving option-ids to their labels.
 */
@Service
@RequiredArgsConstructor
public class CustomFieldsService {

  private static final String TYPE_SINGLE_CHECKBOX = "SINGLE_CHECKBOX";
  private static final String TYPE_TEXTBOX_LONG = "TEXTBOX_LONG";

  private final CustomFieldDefinitionService definitionService;

  public Map<String, CustomFieldContext> resolve(Map<String, Object> raw, String entityType, boolean htmlOutput) {
    if (MapUtils.isEmpty(raw)) {
      return Map.of();
    }
    var definitions = definitionService.getDefinitionsByRefId(entityType);
    Map<String, CustomFieldContext> result = new LinkedHashMap<>();
    raw.forEach((refId, rawValue) -> {
      var definition = definitions.get(refId);
      if (rawValue == null || definition == null || Boolean.FALSE.equals(definition.getVisible())) {
        return;
      }
      var builder = CustomFieldContext.builder()
        .name(definition.getName())
        .type(definition.getType());
      if (rawValue instanceof List<?> list) {
        builder.values(list.stream()
          .filter(Objects::nonNull)
          .map(element -> toListElement(definition, element, htmlOutput))
          .toList());
      } else {
        builder.value(toScalar(definition, rawValue, htmlOutput));
      }
      result.put(refId, builder.build());
    });
    return Collections.unmodifiableMap(result);
  }

  private Object toScalar(CustomField definition, Object rawValue, boolean htmlOutput) {
    if (TYPE_SINGLE_CHECKBOX.equals(definition.getType())) {
      return rawValue; // keep the boolean as-is
    }
    if (isSelect(definition)) {
      return toSelectValue(definition, rawValue); // single-select: {id, label}
    }
    return toText(definition, rawValue, htmlOutput);
  }

  /** List elements: selects become {id, label}; repeatable non-select (e.g. text) stay plain strings. */
  private Object toListElement(CustomField definition, Object element, boolean htmlOutput) {
    return isSelect(definition) ? toSelectValue(definition, element) : toText(definition, element, htmlOutput);
  }

  private CustomFieldOptionValue toSelectValue(CustomField definition, Object element) {
    String id = String.valueOf(element);
    return CustomFieldOptionValue.builder().id(id).label(resolveOptionLabel(definition, id)).build();
  }

  private String toText(CustomField definition, Object element, boolean htmlOutput) {
    String text = String.valueOf(element);
    return TYPE_TEXTBOX_LONG.equals(definition.getType()) ? ExportUtils.toLineBreaks(text, htmlOutput) : text;
  }

  private boolean isSelect(CustomField definition) {
    return definition.getSelectField() != null;
  }

  private String resolveOptionLabel(CustomField definition, String optionId) {
    return Optional.ofNullable(definition.getSelectField())
      .map(SelectField::getOptions)
      .map(SelectFieldOptions::getValues)
      .orElseGet(List::of).stream()
      .filter(option -> optionId.equals(option.getId()))
      .map(SelectFieldOption::getValue)
      .filter(StringUtils::isNotBlank)
      .findFirst()
      .orElse(optionId);
  }
}
