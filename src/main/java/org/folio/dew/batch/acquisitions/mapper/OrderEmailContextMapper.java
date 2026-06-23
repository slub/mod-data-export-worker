package org.folio.dew.batch.acquisitions.mapper;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.dew.batch.acquisitions.services.ConfigurationService;
import org.folio.dew.batch.acquisitions.services.ContributorNameTypeService;
import org.folio.dew.batch.acquisitions.services.IdentifierTypeService;
import org.folio.dew.batch.acquisitions.services.OrganizationsService;
import org.folio.dew.batch.acquisitions.services.UserService;
import org.folio.dew.batch.acquisitions.utils.ExportUtils;
import org.folio.dew.domain.dto.*;
import org.folio.dew.domain.dto.acquisitions.edifact.OrganizationAddress;
import org.folio.dew.domain.dto.templateengine.ContributorContext;
import org.folio.dew.domain.dto.templateengine.CostContext;
import org.folio.dew.domain.dto.templateengine.DetailsContext;
import org.folio.dew.domain.dto.templateengine.OrderContext;
import org.folio.dew.domain.dto.templateengine.OrderEmailContext;
import org.folio.dew.domain.dto.templateengine.OrderLineContext;
import org.folio.dew.domain.dto.templateengine.OrderLineWrapper;
import org.folio.dew.domain.dto.templateengine.OrderWrapper;
import org.folio.dew.domain.dto.templateengine.OrganizationAddressContext;
import org.folio.dew.domain.dto.templateengine.OrganizationContext;
import org.folio.dew.domain.dto.templateengine.ProductIdContext;
import org.folio.dew.domain.dto.templateengine.VendorDetailContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class OrderEmailContextMapper extends EmailContextMapper {

  private final IdentifierTypeService identifierTypeService;
  private final ContributorNameTypeService contributorNameTypeService;
  private final ConfigurationService configurationService;
  private final UserService userService;
  private final OrganizationsService organizationsService;

  public OrderEmailContext buildContext(List<CompositePurchaseOrder> orders, String outputFormat) {
    boolean htmlOutput = isHtmlOutput(outputFormat);
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
      .orderType(Optional.ofNullable(order.getOrderType()).map(CompositePurchaseOrder.OrderTypeEnum::getValue).orElse(""))
      .orderDate(StringUtils.defaultString(ExportUtils.getFormattedDate(order.getDateOrdered())))
      .createdBy(userService.getUserName(Optional.ofNullable(order.getMetadata()).map(Metadata::getCreatedByUserId).map(Object::toString).orElse("")))
      .shipTo(toLineBreaks(configurationService.getAddressConfig(order.getShipTo()), htmlOutput))
      .billTo(toLineBreaks(configurationService.getAddressConfig(order.getBillTo()), htmlOutput))
      .build();
  }

  private OrderLineContext mapOrderLine(PoLine line, boolean htmlOutput) {
    return OrderLineContext.builder()
      .poLineNumber(StringUtils.defaultString(line.getPoLineNumber()))
      .title(StringUtils.defaultString(line.getTitleOrPackage()))
      .publicationDate(StringUtils.defaultString(line.getPublicationDate()))
      .edition(StringUtils.defaultString(line.getEdition()))
      .contributors(mapContributors(line.getContributors()))
      .details(mapDetails(line.getDetails()))
      .cost(mapCost(line.getCost()))
      .vendorDetail(mapVendorDetail(line.getVendorDetail(), htmlOutput))
      .build();
  }

  private CostContext mapCost(Cost cost) {
    var quantityPhysical = Optional.ofNullable(cost).map(Cost::getQuantityPhysical).orElse(0);
    var quantityElectronic = Optional.ofNullable(cost).map(Cost::getQuantityElectronic).orElse(0);
    return CostContext.builder()
      .listUnitPrice(formatDecimal(Optional.ofNullable(cost).map(Cost::getListUnitPrice).orElse(null)))
      .listUnitPriceElectronic(formatDecimal(Optional.ofNullable(cost).map(Cost::getListUnitPriceElectronic).orElse(null)))
      .quantityPhysical(quantityPhysical)
      .quantityElectronic(quantityElectronic)
      .quantity(quantityPhysical + quantityElectronic)
      .estimatedPrice(formatDecimal(Optional.ofNullable(cost).map(Cost::getPoLineEstimatedPrice).orElse(null)))
      .currency(Optional.ofNullable(cost).map(Cost::getCurrency).orElse(""))
      .build();
  }

  private VendorDetailContext mapVendorDetail(VendorDetail vendorDetail, boolean htmlOutput) {
    return VendorDetailContext.builder()
      .instructions(toLineBreaks(Optional.ofNullable(vendorDetail).map(VendorDetail::getInstructions).orElse(""), htmlOutput))
      .build();
  }

  private List<ContributorContext> mapContributors(List<Contributor> contributors) {
    if (CollectionUtils.isEmpty(contributors)) {
      return List.of();
    }
    return contributors.stream()
      .map(this::mapContributor)
      .toList();
  }

  private ContributorContext mapContributor(Contributor contributor) {
    var contributorNameType = StringUtils.defaultString(contributor.getContributorNameTypeId());
    return ContributorContext.builder()
      .contributor(StringUtils.defaultString(contributor.getContributor()))
      .contributorNameType(contributorNameType)
      .contributorNameTypeName(StringUtils.isBlank(contributorNameType) ? "" : StringUtils.defaultString(contributorNameTypeService.getContributorNameTypeName(contributorNameType)))
      .build();
  }

  private DetailsContext mapDetails(Details details) {
    return DetailsContext.builder()
      .productIds(mapProductIds(details))
      .build();
  }

  private List<ProductIdContext> mapProductIds(Details details) {
    if (details == null || CollectionUtils.isEmpty(details.getProductIds())) {
      return List.of();
    }
    return details.getProductIds().stream()
      .map(this::mapProductId)
      .toList();
  }

  private ProductIdContext mapProductId(ProductIdentifier productId) {
    var productIdType = StringUtils.defaultString(productId.getProductIdType());
    return ProductIdContext.builder()
      .productId(StringUtils.defaultString(productId.getProductId()))
      .qualifier(StringUtils.defaultString(productId.getQualifier()))
      .productIdType(productIdType)
      .productIdTypeName(StringUtils.isBlank(productIdType) ? "" : StringUtils.defaultString(identifierTypeService.getIdentifierTypeName(productIdType)))
      .build();
  }
}
