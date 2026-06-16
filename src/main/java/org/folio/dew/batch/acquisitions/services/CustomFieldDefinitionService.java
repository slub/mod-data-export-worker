package org.folio.dew.batch.acquisitions.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.folio.dew.client.CustomFieldsClient;
import org.folio.dew.domain.dto.acquisitions.customfields.CustomField;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Thin cached wrapper over {@link CustomFieldsClient} that returns the custom-field
 * definitions for an entity type, indexed by refId.
 */
@Service
@RequiredArgsConstructor
public class CustomFieldDefinitionService {

  private static final int LIMIT = 1000;

  private final CustomFieldsClient customFieldsClient;

  @Cacheable(cacheNames = "customFieldDefinitions", key = "#entityType")
  public Map<String, CustomField> getDefinitionsByRefId(String entityType) {
    var collection = customFieldsClient.getCustomFields("entityType==" + entityType, LIMIT);
    var definitions = Optional.ofNullable(collection.getCustomFields()).orElseGet(List::of);
    Map<String, CustomField> byRefId = new LinkedHashMap<>();
    for (CustomField definition : definitions) {
      if (StringUtils.isNotBlank(definition.getRefId())) {
        byRefId.put(definition.getRefId(), definition);
      }
    }
    return byRefId;
  }
}
