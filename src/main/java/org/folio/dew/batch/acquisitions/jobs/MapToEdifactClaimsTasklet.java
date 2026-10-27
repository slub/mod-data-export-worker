package org.folio.dew.batch.acquisitions.jobs;

import static org.folio.dew.batch.acquisitions.services.OrdersService.CHUNK_SIZE;
import static org.folio.dew.batch.acquisitions.utils.ExportConfigFields.CLAIM_PIECE_IDS;
import static org.folio.dew.batch.acquisitions.utils.ExportConfigFields.LIB_EDI_TYPE;
import static org.folio.dew.batch.acquisitions.utils.ExportConfigFields.VENDOR_EDI_TYPE;
import static org.folio.dew.batch.acquisitions.utils.ExportUtils.validateField;
import static org.folio.dew.domain.dto.VendorEdiOrdersExportConfig.FileFormatEnum.EDI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.folio.dew.batch.acquisitions.mapper.ExportResourceMapper;
import org.folio.dew.batch.acquisitions.services.OrdersService;
import org.folio.dew.batch.acquisitions.services.OrganizationsService;
import org.folio.dew.domain.dto.CompositePurchaseOrder;
import org.folio.dew.domain.dto.EdiConfig;
import org.folio.dew.domain.dto.Piece;
import org.folio.dew.domain.dto.PoLine;
import org.folio.dew.domain.dto.VendorEdiOrdersExportConfig;
import org.folio.dew.domain.dto.acquisitions.edifact.ExportHolder;
import org.folio.dew.error.NotFoundException;
import org.folio.dew.utils.QueryUtils;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Component
@StepScope
@Log4j2
public class MapToEdifactClaimsTasklet extends MapToEdifactTasklet {

  private static final String NO_PIECES_MATCH_ACQ_METHODS_ERROR_MESSAGE = "No claimed pieces match the acquisition methods of the integration";

  private final ExportResourceMapper edifactMapper;
  private final ExportResourceMapper claimCsvMapper;

  public MapToEdifactClaimsTasklet(ObjectMapper ediObjectMapper, OrganizationsService organizationsService, OrdersService ordersService,
                                   ExportResourceMapper edifactMapper, ExportResourceMapper claimCsvMapper) {
    super(ediObjectMapper, organizationsService, ordersService);
    this.edifactMapper = edifactMapper;
    this.claimCsvMapper = claimCsvMapper;
  }

  @Override
  protected ExportResourceMapper getExportResourceMapper(VendorEdiOrdersExportConfig ediOrdersExportConfig) {
    return switch (ediOrdersExportConfig.getFileFormat()) {
      case EDI -> edifactMapper;
      case CSV -> claimCsvMapper;
    };
  }

  @Override
  protected List<String> getExportConfigMissingFields(VendorEdiOrdersExportConfig ediOrdersExportConfig) {
    List<String> missingFields = new ArrayList<>();
    validateField(CLAIM_PIECE_IDS.getName(), ediOrdersExportConfig.getClaimPieceIds(), CollectionUtils::isNotEmpty, missingFields);

    if (ediOrdersExportConfig.getFileFormat() == EDI) {
      var ediConfig = ediOrdersExportConfig.getEdiConfig();
      validateField(LIB_EDI_TYPE.getName(), ediConfig.getLibEdiType(), Objects::nonNull, missingFields);
      validateField(VENDOR_EDI_TYPE.getName(), ediConfig.getVendorEdiType(), Objects::nonNull, missingFields);
    }
    return missingFields;
  }

  @Override
  protected ExportHolder buildEdifactExportHolder(VendorEdiOrdersExportConfig ediExportConfig, Map<String, Object> jobParameters) {
    var pieces = ordersService.getPiecesByIdsAndReceivingStatus(ediExportConfig.getClaimPieceIds(), Piece.ReceivingStatusEnum.CLAIM_SENT);
    if (pieces.isEmpty()) {
      throw new NotFoundException(Piece.class);
    }

    var compOrdersMap = StreamEx.ofSubLists(pieces.stream().map(Piece::getPoLineId).distinct().toList(), CHUNK_SIZE)
      .map(QueryUtils::convertIdsToCqlQuery)
      .map(this::getCompositeOrders)
      .flatMap(Collection::stream)
      .groupingBy(CompositePurchaseOrder::getId);

    // Composite orders are fetched in chunks based on po lines, so there can be multiple orders with the same id
    // and different po lines in the map values. We need to merge them into one order with all po lines.
    var compOrders = compOrdersMap.values().stream()
      .filter(CollectionUtils::isNotEmpty)
      .map(orders -> {
        var poLines = orders.stream()
          .map(CompositePurchaseOrder::getPoLines)
          .flatMap(Collection::stream)
          .toList();
        return orders.getFirst().poLines(poLines);
      }).toList();

    return filterByAcquisitionMethods(new ExportHolder(compOrders, pieces), ediExportConfig);
  }

  /**
   * Keeps only the pieces whose po line has one of the acquisition methods selected on the integration,
   * together with their po lines and orders. Without selected acquisition methods all pieces are kept.
   */
  private ExportHolder filterByAcquisitionMethods(ExportHolder holder, VendorEdiOrdersExportConfig ediExportConfig) {
    var acqMethods = Optional.ofNullable(ediExportConfig.getEdiConfig())
      .map(EdiConfig::getDefaultAcquisitionMethods)
      .orElse(List.of());
    if (acqMethods.isEmpty()) {
      return holder;
    }

    var acqMethodIds = acqMethods.stream().map(UUID::toString).collect(Collectors.toSet());
    var compOrders = holder.orders().stream()
      .map(order -> order.poLines(order.getPoLines().stream()
        .filter(poLine -> acqMethodIds.contains(poLine.getAcquisitionMethod()))
        .toList()))
      .filter(order -> CollectionUtils.isNotEmpty(order.getPoLines()))
      .toList();
    var poLineIds = compOrders.stream()
      .flatMap(order -> order.getPoLines().stream())
      .map(PoLine::getId)
      .collect(Collectors.toSet());
    var pieces = holder.pieces().stream()
      .filter(piece -> poLineIds.contains(piece.getPoLineId()))
      .toList();

    log.info("filterByAcquisitionMethods:: Kept {} of {} claimed pieces for acquisition methods: {}",
      pieces.size(), holder.pieces().size(), acqMethodIds);
    if (pieces.isEmpty()) {
      throw new NotFoundException(NO_PIECES_MATCH_ACQ_METHODS_ERROR_MESSAGE);
    }
    return new ExportHolder(compOrders, pieces);
  }

}
