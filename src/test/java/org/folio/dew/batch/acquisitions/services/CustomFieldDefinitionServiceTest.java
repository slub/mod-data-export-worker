package org.folio.dew.batch.acquisitions.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.folio.dew.client.CustomFieldsClient;
import org.folio.dew.domain.dto.acquisitions.customfields.CustomField;
import org.folio.dew.domain.dto.acquisitions.customfields.CustomFieldCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class CustomFieldDefinitionServiceTest {

  private static final String ENTITY_TYPE = "po_line";

  @Mock
  private CustomFieldsClient customFieldsClient;
  @InjectMocks
  private CustomFieldDefinitionService service;

  @Test
  void getDefinitionsByRefId_indexesByRefId_skippingBlankRefIds() {
    var collection = collectionOf(
      field("area", "Area"),
      field("", "Blank refId"),
      field(null, "Null refId"));
    when(customFieldsClient.getCustomFields(anyString(), anyInt())).thenReturn(collection);

    var result = service.getDefinitionsByRefId(ENTITY_TYPE);

    assertThat(result).containsOnlyKeys("area");
    assertThat(result.get("area").getName()).isEqualTo("Area");
  }

  @Test
  void getDefinitionsByRefId_onClientError_returnsEmptyMapWithoutThrowing() {
    when(customFieldsClient.getCustomFields(anyString(), anyInt()))
      .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

    var result = service.getDefinitionsByRefId(ENTITY_TYPE);

    assertThat(result).isEmpty();
  }

  private static CustomFieldCollection collectionOf(CustomField... fields) {
    var collection = new CustomFieldCollection();
    collection.setCustomFields(List.of(fields));
    return collection;
  }

  private static CustomField field(String refId, String name) {
    var field = new CustomField();
    field.setRefId(refId);
    field.setName(name);
    return field;
  }
}
