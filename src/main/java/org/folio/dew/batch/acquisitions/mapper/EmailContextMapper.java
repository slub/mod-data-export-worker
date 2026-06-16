package org.folio.dew.batch.acquisitions.mapper;

import java.math.BigDecimal;

public abstract class EmailContextMapper {

  protected String formatDecimal(BigDecimal value) {
    return value != null ? value.toPlainString() : "";
  }
}
