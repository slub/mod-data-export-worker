package org.folio.dew.batch.acquisitions.mapper;

import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;

public abstract class EmailContextMapper {

  protected static boolean isHtmlOutput(String outputFormat) {
    return outputFormat != null && outputFormat.toLowerCase().contains("html");
  }

  protected String toLineBreaks(String value, boolean htmlOutput) {
    String safe = StringUtils.defaultString(value);
    return htmlOutput ? safe.replace("\n", "<br>") : safe;
  }

  protected String formatDecimal(BigDecimal value) {
    return value != null ? value.toPlainString() : "";
  }
}
