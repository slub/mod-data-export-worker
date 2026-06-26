package org.folio.dew.batch.acquisitions.services;

import org.folio.dew.client.IdentifierTypeClient;
import org.folio.dew.domain.dto.acquisitions.edifact.IdentifierType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class IdentifierTypeService {

  private final IdentifierTypeClient identifierTypeClient;

  @Cacheable(cacheNames = "identifierTypes", unless = "#result == null")
  public String getIdentifierTypeName(String id) {
    try {
      IdentifierType identifierType = identifierTypeClient.getIdentifierType(id);
      return identifierType != null ? identifierType.getName() : "";
    } catch (Exception e) {
      log.warn("getIdentifierTypeName:: Cannot find identifier type by id: '{}'", id, e);
      return null;
    }
  }
}
