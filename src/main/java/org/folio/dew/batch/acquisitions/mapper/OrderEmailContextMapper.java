package org.folio.dew.batch.acquisitions.mapper;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.dew.batch.acquisitions.services.ConfigurationService;
import org.folio.dew.batch.acquisitions.services.CustomFieldsService;
import org.folio.dew.batch.acquisitions.services.IdentifierTypeService;
import org.folio.dew.batch.acquisitions.services.OrganizationsService;
import org.folio.dew.batch.acquisitions.services.UserService;
import org.folio.dew.batch.acquisitions.utils.ExportUtils;
import org.folio.dew.domain.dto.*;
import org.folio.dew.domain.dto.acquisitions.edifact.OrganizationAddress;
import org.folio.dew.domain.dto.templateengine.OrderContext;
import org.folio.dew.domain.dto.templateengine.OrderEmailContext;
import org.folio.dew.domain.dto.templateengine.OrderLineContext;
import org.folio.dew.domain.dto.templateengine.OrderLineWrapper;
import org.folio.dew.domain.dto.templateengine.OrderWrapper;
import org.folio.dew.domain.dto.templateengine.OrganizationAddressContext;
import org.folio.dew.domain.dto.templateengine.OrganizationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderEmailContextMapper extends EmailContextMapper {

  private static final String ENTITY_TYPE_PURCHASE_ORDER = "purchase_order";
  private static final String ENTITY_TYPE_PO_LINE = "po_line";

  private final IdentifierTypeService identifierTypeService;
  private final ConfigurationService configurationService;
  private final UserService userService;
  private final OrganizationsService organizationsService;
  private final CustomFieldsService customFieldsService;

  public OrderEmailContext buildContext(List<CompositePurchaseOrder> orders, String outputFormat) {
    boolean htmlOutput = ExportUtils.isHtmlOutput(outputFormat);
    var orderWrappers = orders.stream()
      .map(order -> new OrderWrapper(
        mapOrder(order, htmlOutput),
        order.getPoLines().stream()
          .map(line -> new OrderLineWrapper(mapOrderLine(line, htmlOutput)))
          .toList()))
      .toList();
    return OrderEmailContext.builder()
      .organization(mapOrganization(orders))
      .orders(orderWrappers)
      .build();
  }

  private OrganizationContext mapOrganization(List<CompositePurchaseOrder> orders) {
    return orders.stream()
      .map(CompositePurchaseOrder::getVendor)
      .filter(Objects::nonNull)
      .findFirst()
      .map(vendorId -> organizationsService.getOrganizationById(vendorId.toString()))
      .map(org -> OrganizationContext.builder()
        .name(StringUtils.defaultString(org.getName()))
        .primaryAddress(pickPrimaryAddress(org.getAddresses()))
        .build())
      .orElseGet(this::emptyOrganizationContext);
  }

  private OrganizationContext emptyOrganizationContext() {
    return OrganizationContext.builder()
      .name("")
      .primaryAddress(emptyOrganizationAddressContext())
      .build();
  }

  private OrganizationAddressContext emptyOrganizationAddressContext() {
    return OrganizationAddressContext.builder()
      .addressLine1("")
      .city("")
      .zipCode("")
      .country("")
      .build();
  }

  private OrganizationAddressContext pickPrimaryAddress(List<OrganizationAddress> addresses) {
    return pickPrimary(addresses, addr -> Boolean.TRUE.equals(addr.getIsPrimary()))
      .map(addr -> OrganizationAddressContext.builder()
        .addressLine1(StringUtils.defaultString(addr.getAddressLine1()))
        .city(StringUtils.defaultString(addr.getCity()))
        .zipCode(StringUtils.defaultString(addr.getZipCode()))
        .country(StringUtils.defaultString(addr.getCountry()))
        .build())
      .orElseGet(this::emptyOrganizationAddressContext);
  }

  private <T> Optional<T> pickPrimary(List<T> items, Predicate<T> isPrimary) {
    if (CollectionUtils.isEmpty(items)) {
      return Optional.empty();
    }
    return items.stream()
      .filter(isPrimary)
      .findFirst();
  }

  private OrderContext mapOrder(CompositePurchaseOrder order, boolean htmlOutput) {
    return OrderContext.builder()
      .poNumber(StringUtils.defaultString(order.getPoNumber()))
      .orderDate(StringUtils.defaultString(ExportUtils.getFormattedDate(order.getDateOrdered())))
      .createdBy(userService.getUserName(Optional.ofNullable(order.getMetadata()).map(Metadata::getCreatedByUserId).map(Object::toString).orElse("")))
      .shipTo(ExportUtils.toLineBreaks(configurationService.getAddressConfig(order.getShipTo()), htmlOutput))
      .billTo(ExportUtils.toLineBreaks(configurationService.getAddressConfig(order.getBillTo()), htmlOutput))
      .customFields(customFieldsService.resolve(order.getCustomFields(), ENTITY_TYPE_PURCHASE_ORDER, htmlOutput))
      .build();
  }

  private OrderLineContext mapOrderLine(PoLine line, boolean htmlOutput) {
    var cost = line.getCost();
    var quantityPhysical = Optional.ofNullable(cost).map(Cost::getQuantityPhysical).orElse(0);
    var quantityElectronic = Optional.ofNullable(cost).map(Cost::getQuantityElectronic).orElse(0);
    return OrderLineContext.builder()
      .poLineNumber(StringUtils.defaultString(line.getPoLineNumber()))
      .title(StringUtils.defaultString(line.getTitleOrPackage()))
      .publicationDate(StringUtils.defaultString(line.getPublicationDate()))
      .edition(StringUtils.defaultString(line.getEdition()))
      .productIdentifier(mapProductIdentifiers(line.getDetails()))
      .productIdentifierType(mapProductIdentifierTypes(line.getDetails()))
      .listUnitPrice(formatDecimal(Optional.ofNullable(cost).map(Cost::getListUnitPrice).orElse(null)))
      .listUnitPriceElectronic(formatDecimal(Optional.ofNullable(cost).map(Cost::getListUnitPriceElectronic).orElse(null)))
      .quantityPhysical(quantityPhysical)
      .quantityElectronic(quantityElectronic)
      .quantity(quantityPhysical + quantityElectronic)
      .estimatedPrice(formatDecimal(Optional.ofNullable(cost).map(Cost::getPoLineEstimatedPrice).orElse(null)))
      .currency(Optional.ofNullable(cost).map(Cost::getCurrency).orElse(""))
      .customFields(customFieldsService.resolve(line.getCustomFields(), ENTITY_TYPE_PO_LINE, htmlOutput))
      .build();
  }

  private String mapProductIdentifiers(Details details) {
    if (details == null || CollectionUtils.isEmpty(details.getProductIds())) {
      return "";
    }
    return details.getProductIds().stream()
      .map(ProductIdentifier::getProductId)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining("; "));
  }

  private String mapProductIdentifierTypes(Details details) {
    if (details == null || CollectionUtils.isEmpty(details.getProductIds())) {
      return "";
    }
    return details.getProductIds().stream()
      .map(ProductIdentifier::getProductIdType)
      .filter(StringUtils::isNotBlank)
      .map(identifierTypeService::getIdentifierTypeName)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining("; "));
  }
}
