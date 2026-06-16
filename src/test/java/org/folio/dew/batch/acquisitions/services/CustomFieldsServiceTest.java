package org.folio.dew.batch.acquisitions.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.folio.dew.domain.dto.acquisitions.customfields.CustomField;
import org.folio.dew.domain.dto.acquisitions.customfields.SelectField;
import org.folio.dew.domain.dto.acquisitions.customfields.SelectFieldOption;
import org.folio.dew.domain.dto.acquisitions.customfields.SelectFieldOptions;
import org.folio.dew.domain.dto.templateengine.CustomFieldContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFieldsServiceTest {

  private static final String ENTITY_TYPE = "po_line";

  @Mock
  private CustomFieldDefinitionService definitionService;
  @InjectMocks
  private CustomFieldsService service;

  @Test
  void resolve_emptyOrNullRaw_returnsEmptyMap() {
    assertThat(service.resolve(null, ENTITY_TYPE, true)).isEmpty();
    assertThat(service.resolve(Map.of(), ENTITY_TYPE, true)).isEmpty();
  }

  @Test
  void resolve_singleSelect_resolvesOptionLabelAsScalarValue() {
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(Map.of(
      "area", select("area", "Area", false, option("opt_1", "History"), option("opt_2", "Art"))));

    var result = service.resolve(Map.of("area", "opt_2"), ENTITY_TYPE, true);

    var ctx = result.get("area");
    assertThat(ctx.getName()).isEqualTo("Area");
    assertThat(ctx.getValue()).isEqualTo("Art");
    assertThat(ctx.getValues()).isNull();
  }

  @Test
  void resolve_multiSelect_resolvesEachOptionAsIdValuePair() {
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(Map.of(
      "subjects", select("subjects", "Subject", true, option("opt_1", "History"), option("opt_3", "Art"))));

    var result = service.resolve(Map.of("subjects", List.of("opt_1", "opt_3")), ENTITY_TYPE, true);

    var ctx = result.get("subjects");
    assertThat(ctx.getValue()).isNull();
    assertThat(ctx.getValues()).hasSize(2);
    assertThat(ctx.getValues().get(0).getId()).isEqualTo("opt_1");
    assertThat(ctx.getValues().get(0).getValue()).isEqualTo("History");
    assertThat(ctx.getValues().get(1).getId()).isEqualTo("opt_3");
    assertThat(ctx.getValues().get(1).getValue()).isEqualTo("Art");
  }

  @Test
  void resolve_unknownOptionId_fallsBackToId() {
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(Map.of(
      "area", select("area", "Area", false, option("opt_1", "History"))));

    var result = service.resolve(Map.of("area", "opt_9"), ENTITY_TYPE, true);

    assertThat(result.get("area").getValue()).isEqualTo("opt_9");
  }

  @Test
  void resolve_checkbox_keepsBooleanAsIs() {
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(Map.of(
      "expedite", textType("expedite", "Expedite", "SINGLE_CHECKBOX")));

    var result = service.resolve(Map.of("expedite", true), ENTITY_TYPE, true);

    assertThat(result.get("expedite").getValue()).isEqualTo(Boolean.TRUE);
  }

  @Test
  void resolve_textboxLong_convertsNewlinesToBrOnlyForHtml() {
    var defs = Map.of("notes", textType("notes", "Notes", "TEXTBOX_LONG"));
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(defs);

    var html = service.resolve(Map.of("notes", "line1\nline2"), ENTITY_TYPE, true);
    assertThat(html.get("notes").getValue()).isEqualTo("line1<br>line2");

    var plain = service.resolve(Map.of("notes", "line1\nline2"), ENTITY_TYPE, false);
    assertThat(plain.get("notes").getValue()).isEqualTo("line1\nline2");
  }

  @Test
  void resolve_textboxShortAndDate_passThroughRaw() {
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(Map.of(
      "ref", textType("ref", "Vendor ref", "TEXTBOX_SHORT"),
      "ship", textType("ship", "Ship date", "DATE_PICKER")));

    var result = service.resolve(new LinkedHashMap<>(Map.of("ref", "X-9912", "ship", "2026-06-01")), ENTITY_TYPE, true);

    assertThat(result.get("ref").getValue()).isEqualTo("X-9912");
    assertThat(result.get("ship").getValue()).isEqualTo("2026-06-01");
  }

  @Test
  void resolve_repeatableText_returnsValuesWithoutId() {
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(Map.of(
      "refs", textType("refs", "Vendor refs", "TEXTBOX_SHORT")));

    var result = service.resolve(Map.of("refs", List.of("X-9912", "Y-4488")), ENTITY_TYPE, true);

    var values = result.get("refs").getValues();
    assertThat(values).hasSize(2);
    assertThat(values.get(0).getId()).isNull();
    assertThat(values.get(0).getValue()).isEqualTo("X-9912");
    assertThat(values.get(1).getValue()).isEqualTo("Y-4488");
  }

  @Test
  void resolve_nonVisibleField_isExcluded() {
    var hidden = textType("secret", "Secret", "TEXTBOX_SHORT");
    hidden.setVisible(false);
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(Map.of("secret", hidden));

    var result = service.resolve(Map.of("secret", "value"), ENTITY_TYPE, true);

    assertThat(result).doesNotContainKey("secret");
  }

  @Test
  void resolve_unknownRefId_isSkipped() {
    when(definitionService.getDefinitionsByRefId(ENTITY_TYPE)).thenReturn(Map.of(
      "known", textType("known", "Known", "TEXTBOX_SHORT")));

    var result = service.resolve(new LinkedHashMap<>(Map.of("known", "a", "ghost", "b")), ENTITY_TYPE, true);

    assertThat(result).containsKey("known").doesNotContainKey("ghost");
  }

  private static CustomField textType(String refId, String name, String type) {
    var field = new CustomField();
    field.setRefId(refId);
    field.setName(name);
    field.setType(type);
    field.setVisible(true);
    return field;
  }

  private static CustomField select(String refId, String name, boolean multi, SelectFieldOption... options) {
    var field = textType(refId, name, multi ? "MULTI_SELECT_DROPDOWN" : "SINGLE_SELECT_DROPDOWN");
    var selectFieldOptions = new SelectFieldOptions();
    selectFieldOptions.setValues(List.of(options));
    var selectField = new SelectField();
    selectField.setOptions(selectFieldOptions);
    field.setSelectField(selectField);
    return field;
  }

  private static SelectFieldOption option(String id, String value) {
    var option = new SelectFieldOption();
    option.setId(id);
    option.setValue(value);
    return option;
  }
}
