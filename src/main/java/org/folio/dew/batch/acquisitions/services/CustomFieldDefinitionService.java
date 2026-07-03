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
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Thin cached wrapper over {@link CustomFieldsClient} that returns the custom-field
 * definitions for an entity type, indexed by refId.
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class CustomFieldDefinitionService {

  private static final int LIMIT = 1000;

  private final CustomFieldsClient customFieldsClient;

  @Cacheable(cacheNames = "customFieldDefinitions", key = "#entityType")
  public Map<String, CustomField> getDefinitionsByRefId(String entityType) {
    List<CustomField> definitions;
    try {
      var collection = customFieldsClient.getCustomFields("entityType==" + entityType, LIMIT);
      definitions = Optional.ofNullable(collection.getCustomFields()).orElseGet(List::of);
    } catch (RestClientException e) {
      // Custom fields are an optional enrichment of the export - if the definitions cannot be
      // fetched (e.g. interface not present, routing/permission failure), degrade gracefully so
      // the export still goes out without custom-field tokens rather than failing the job.
      log.warn("getDefinitionsByRefId:: Could not fetch custom-field definitions for entityType={}, "
        + "continuing without them: {}", entityType, e.getMessage());
      return Map.of();
    }
    Map<String, CustomField> byRefId = new LinkedHashMap<>();
    for (CustomField definition : definitions) {
      if (StringUtils.isNotBlank(definition.getRefId())) {
        byRefId.put(definition.getRefId(), definition);
      }
    }
    return byRefId;
  }
}
