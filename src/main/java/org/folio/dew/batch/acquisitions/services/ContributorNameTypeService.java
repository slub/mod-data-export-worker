package org.folio.dew.batch.acquisitions.services;

import org.folio.dew.client.ContributorNameTypeClient;
import org.folio.dew.domain.dto.acquisitions.edifact.ContributorNameType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class ContributorNameTypeService {

  private final ContributorNameTypeClient contributorNameTypeClient;

  @Cacheable(cacheNames = "contributorNameTypes", unless = "#result == null")
  public String getContributorNameTypeName(String id) {
    try {
      ContributorNameType contributorNameType = contributorNameTypeClient.getContributorNameType(id);
      return contributorNameType != null ? contributorNameType.getName() : "";
    } catch (Exception e) {
      log.warn("getContributorNameTypeName:: Cannot find contributor name type by id: '{}'", id, e);
      return null;
    }
  }
}
