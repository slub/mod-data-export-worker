package org.folio.dew.batch.acquisitions.services;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dew.client.TenantAddressesClient;
import org.folio.dew.domain.dto.acquisitions.edifact.TenantAddress;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static java.util.Optional.ofNullable;

@Service
@RequiredArgsConstructor
public class ConfigurationService {

  private static final Logger logger = LogManager.getLogger();
  private static final String ADDRESS = "address";

  private final TenantAddressesClient tenantAddressesClient;

  @Cacheable(cacheNames = "addressConfiguration")
  public String getAddressConfig(UUID shipToConfigId) {
    return ofNullable(getTenantAddress(shipToConfigId))
      .map(TenantAddress::getAddress)
      .orElse("");
  }

  @Cacheable(cacheNames = "tenantAddress", unless = "#result == null")
  public TenantAddress getTenantAddress(UUID shipToConfigId) {
    if (shipToConfigId == null) {
      logger.warn("getTenantAddress:: shipToConfigId is null");
      return null;
    }
    try {
      TenantAddress addressResponse = tenantAddressesClient.getById(shipToConfigId.toString());

      if (addressResponse == null) {
        logger.warn("getTenantAddress:: No address found for id '{}'", shipToConfigId);
        return null;
      }

      logger.info("getTenantAddress:: Found address with id '{}'", shipToConfigId);
      return addressResponse;
    } catch (Exception e) {
      logger.warn("getTenantAddress:: Cannot find address by id: '{}'", shipToConfigId, e);
      return null;
    }
  }
}
