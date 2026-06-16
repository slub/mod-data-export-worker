package org.folio.dew.client;

import org.folio.dew.domain.dto.acquisitions.customfields.CustomFieldCollection;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "custom-fields", accept = MediaType.APPLICATION_JSON_VALUE)
public interface CustomFieldsClient {

  @GetExchange
  CustomFieldCollection getCustomFields(@RequestParam("query") String query, @RequestParam("limit") int limit);
}
