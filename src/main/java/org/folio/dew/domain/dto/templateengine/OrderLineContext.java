package org.folio.dew.domain.dto.templateengine;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderLineContext {

  private String poLineNumber;
  private String title;
  private String publicationDate;
  private String edition;
  private DetailsContext details;
  private CostContext cost;
  private VendorDetailContext vendorDetail;
}
