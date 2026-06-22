package org.folio.dew.domain.dto.templateengine;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductIdContext {

  private String productId;
  private String qualifier;
  private String productIdType;
  private String productIdTypeName;
}