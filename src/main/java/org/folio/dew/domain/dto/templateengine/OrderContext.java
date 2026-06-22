package org.folio.dew.domain.dto.templateengine;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderContext {

  private String poNumber;
  private String orderType;
  private String orderDate;
  private String createdBy;
  private String shipTo;
  private String billTo;
}
