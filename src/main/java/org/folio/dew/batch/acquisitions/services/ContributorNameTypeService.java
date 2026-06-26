package org.folio.dew.batch.acquisitions.services;

import org.folio.dew.client.ContributorNameTypeClient;
import org.folio.dew.domain.dto.acquisitions.edifact.ContributorNameType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContributorNameTypeService {
  @Autowired
  private final ContributorNameTypeClient contributorNameTypeClient;

  private ContributorNameType getContributorNameType(String id) {
    return contributorNameTypeClient.getContributorNameType(id);
  }

  @Cacheable(cacheNames = "contributorNameTypes")
  public String getContributorNameTypeName(String id) {
    ContributorNameType contributorNameType = getContributorNameType(id);
    String name = "";

    if (contributorNameType != null) {
      name = contributorNameType.getName();
    }

    return name;
  }
}
