package org.folio.dew.batch.acquisitions.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.dew.batch.acquisitions.services.ConfigurationService;
import org.folio.dew.batch.acquisitions.services.IdentifierTypeService;
import org.folio.dew.batch.acquisitions.services.OrganizationsService;
import org.folio.dew.batch.acquisitions.services.UserService;
import org.folio.dew.domain.dto.CompositePurchaseOrder;
import org.folio.dew.domain.dto.acquisitions.edifact.Organization;
import org.folio.dew.domain.dto.acquisitions.edifact.OrganizationAddress;
import org.folio.dew.domain.dto.templateengine.OrderEmailContext;
import org.folio.dew.domain.dto.templateengine.OrderLineContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.dew.utils.TestUtils.getMockData;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEmailContextMapperTest {

  private static final UUID SHIP_TO_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID BILL_TO_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String SHIP_TO_ADDRESS = "Library Loading Dock, 123 Main St, Springfield IL";
  private static final String BILL_TO_ADDRESS = "Accounts Payable, PO Box 42, Springfield IL";
  private static final String VENDOR_UUID = "50fb6ae0-cdf1-11e8-a8d5-f2801f1b9fd1";

  @Mock
  private IdentifierTypeService identifierTypeService;
  @Mock
  private ConfigurationService configurationService;
  @Mock
  private UserService userService;
  @Mock
  private OrganizationsService organizationsService;

  private OrderEmailContextMapper mapper;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mapper = new OrderEmailContextMapper(identifierTypeService, configurationService, userService, organizationsService);
    objectMapper = new ObjectMapper();
    lenient().when(identifierTypeService.getIdentifierTypeName(anyString())).thenReturn("ISBN");
    lenient().when(configurationService.getAddressConfig(any())).thenReturn("");
    lenient().when(userService.getUserName(anyString())).thenReturn("");
    lenient().when(organizationsService.getOrganizationById(anyString())).thenReturn(new Organization());
  }

  @Test
  void buildContext_mapsOrderFields() throws IOException {
    when(configurationService.getAddressConfig(SHIP_TO_UUID)).thenReturn(SHIP_TO_ADDRESS);
    when(configurationService.getAddressConfig(BILL_TO_UUID)).thenReturn(BILL_TO_ADDRESS);
    when(userService.getUserName("7a626480-284e-5b55-9cf2-db32f93956cf")).thenReturn("John Doe");
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    assertThat(ctx.getOrders()).hasSize(1);
    var wrapper = ctx.getOrders().get(0);
    assertThat(wrapper.order().getPoNumber()).isEqualTo("10000");
    assertThat(wrapper.order().getOrderType()).isEqualTo("One-Time");
    assertThat(wrapper.order().getOrderDate()).isEqualTo("2021-01-15");
    assertThat(wrapper.order().getCreatedBy()).isEqualTo("John Doe");
    assertThat(wrapper.order().getShipTo()).isEqualTo(SHIP_TO_ADDRESS);
    assertThat(wrapper.order().getBillTo()).isEqualTo(BILL_TO_ADDRESS);
  }

  @Test
  void buildContext_shipToBillToNewlines_renderedAsBrTags() throws IOException {
    when(configurationService.getAddressConfig(SHIP_TO_UUID))
      .thenReturn("SLUB Dresden\nZellescher Weg 18\n01069 Dresden");
    when(configurationService.getAddressConfig(BILL_TO_UUID))
      .thenReturn("Accounts Payable\nPO Box 42\nSpringfield IL");
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    assertThat(ctx.getOrders().get(0).order().getShipTo())
      .isEqualTo("SLUB Dresden<br>Zellescher Weg 18<br>01069 Dresden");
    assertThat(ctx.getOrders().get(0).order().getBillTo())
      .isEqualTo("Accounts Payable<br>PO Box 42<br>Springfield IL");
  }

  @Test
  void buildContext_shipToBillToNewlines_plainTextKeepsNewlines() throws IOException {
    when(configurationService.getAddressConfig(SHIP_TO_UUID))
      .thenReturn("SLUB Dresden\nZellescher Weg 18\n01069 Dresden");
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/plain");

    assertThat(ctx.getOrders().get(0).order().getShipTo())
      .isEqualTo("SLUB Dresden\nZellescher Weg 18\n01069 Dresden");
  }

  @Test
  void buildContext_shipToBillToNotResolved_returnsEmptyString() throws IOException {
    var order = loadOrder("edifact/acquisitions/minimalistic_composite_purchase_order.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    assertThat(ctx.getOrders().get(0).order().getShipTo()).isEmpty();
    assertThat(ctx.getOrders().get(0).order().getBillTo()).isEmpty();
  }

  @Test
  void buildContext_mapsOrderLineFields() throws IOException {
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    assertThat(ctx.getOrders()).hasSize(1);
    var lines = ctx.getOrders().get(0).orderLines();
    assertThat(lines).hasSize(1);

    OrderLineContext line = lines.get(0).orderLine();
    assertThat(line.getPoLineNumber()).isEqualTo("10000-1");
    assertThat(line.getTitle()).isEqualTo("Futures, biometrics and neuroscience research Luiz Moutinho, Mladen Sokele, editors");
    assertThat(line.getPublicationDate()).isEqualTo("2021");
    assertThat(line.getEdition()).isEqualTo("2nd ed.");
    assertThat(line.getDetails().getProductIds()).hasSize(1);
    var productId = line.getDetails().getProductIds().get(0);
    assertThat(productId.getProductId()).isEqualTo("9783319643991");
    assertThat(productId.getQualifier()).isEqualTo("(paperback)");
    assertThat(productId.getProductIdType()).isEqualTo("8261054f-be78-422d-bd51-4ed9f33c3422");
    assertThat(productId.getProductIdTypeName()).isEqualTo("ISBN");
    assertThat(line.getCost().getListUnitPrice()).isEqualTo("2.0");
    assertThat(line.getCost().getCurrency()).isEqualTo("USD");
    assertThat(line.getCost().getQuantity()).isEqualTo(1);
    assertThat(line.getCost().getEstimatedPrice()).isEqualTo("1.8");
    assertThat(line.getVendorDetail().getInstructions()).isEqualTo("Handle with care");
  }

  @Test
  void buildContext_multipleOrders_producesOneWrapperPerOrder() throws IOException {
    var order1 = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");
    var order2 = loadOrder("edifact/acquisitions/minimalistic_composite_purchase_order.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order1, order2), "text/html");

    assertThat(ctx.getOrders()).hasSize(2);
  }

  @Test
  void buildContext_emptyOrders_returnsEmptyList() {
    OrderEmailContext ctx = mapper.buildContext(List.of(), "text/html");

    assertThat(ctx.getOrders()).isEmpty();
    assertThat(ctx.getOrganization().getName()).isEmpty();
    assertThat(ctx.getOrganization().getPrimaryAddress().getAddressLine1()).isEmpty();
  }

  @Test
  void buildContext_mapsOrganizationFields() throws IOException {
    var org = new Organization();
    org.setName("Acme Books");
    org.setCode("ACME");
    org.setAddresses(List.of(
      buildAddress("321 Other St", "Otherville", "99999", "USA", false),
      buildAddress("100 Main St", "Springfield", "12345", "USA", true)));
    when(organizationsService.getOrganizationById(VENDOR_UUID)).thenReturn(org);
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    var organization = ctx.getOrganization();
    assertThat(organization.getName()).isEqualTo("Acme Books");
    assertThat(organization.getPrimaryAddress().getAddressLine1()).isEqualTo("100 Main St");
    assertThat(organization.getPrimaryAddress().getCity()).isEqualTo("Springfield");
    assertThat(organization.getPrimaryAddress().getZipCode()).isEqualTo("12345");
    assertThat(organization.getPrimaryAddress().getCountry()).isEqualTo("USA");
  }

  @Test
  void buildContext_noPrimaryFlagged_returnsEmpty() throws IOException {
    var org = new Organization();
    org.setAddresses(List.of(buildAddress("First St", "FirstCity", "11111", "USA", null)));
    when(organizationsService.getOrganizationById(VENDOR_UUID)).thenReturn(org);
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    assertThat(ctx.getOrganization().getPrimaryAddress().getAddressLine1()).isEmpty();
    assertThat(ctx.getOrganization().getPrimaryAddress().getCity()).isEmpty();
    assertThat(ctx.getOrganization().getPrimaryAddress().getZipCode()).isEmpty();
    assertThat(ctx.getOrganization().getPrimaryAddress().getCountry()).isEmpty();
  }

  @Test
  void buildContext_organizationWithEmptyLists_returnsEmptyStrings() throws IOException {
    var org = new Organization();
    org.setName("Acme Books");
    when(organizationsService.getOrganizationById(VENDOR_UUID)).thenReturn(org);
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    assertThat(ctx.getOrganization().getName()).isEqualTo("Acme Books");
    assertThat(ctx.getOrganization().getPrimaryAddress().getAddressLine1()).isEmpty();
    assertThat(ctx.getOrganization().getPrimaryAddress().getCity()).isEmpty();
    assertThat(ctx.getOrganization().getPrimaryAddress().getZipCode()).isEmpty();
    assertThat(ctx.getOrganization().getPrimaryAddress().getCountry()).isEmpty();
  }

  private OrganizationAddress buildAddress(String line1, String city, String zip, String country, Boolean isPrimary) {
    var address = new OrganizationAddress();
    address.setAddressLine1(line1);
    address.setCity(city);
    address.setZipCode(zip);
    address.setCountry(country);
    address.setIsPrimary(isPrimary);
    return address;
  }

  @Test
  void buildContext_nullCost_doesNotThrow() throws IOException {
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");
    order.getPoLines().get(0).setCost(null);

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    OrderLineContext line = ctx.getOrders().get(0).orderLines().get(0).orderLine();
    assertThat(line.getCost().getListUnitPrice()).isEmpty();
    assertThat(line.getCost().getCurrency()).isEmpty();
    assertThat(line.getCost().getQuantity()).isZero();
  }

  @Test
  void buildContext_instructionsNewlines_renderedAsBrTags() throws IOException {
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");
    order.getPoLines().get(0).getVendorDetail().setInstructions("Line 1\nLine 2");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    OrderLineContext line = ctx.getOrders().get(0).orderLines().get(0).orderLine();
    assertThat(line.getVendorDetail().getInstructions()).isEqualTo("Line 1<br>Line 2");
  }

  @Test
  void buildContext_instructionsNewlines_plainTextKeepsNewlines() throws IOException {
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");
    order.getPoLines().get(0).getVendorDetail().setInstructions("Line 1\nLine 2");

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/plain");

    OrderLineContext line = ctx.getOrders().get(0).orderLines().get(0).orderLine();
    assertThat(line.getVendorDetail().getInstructions()).isEqualTo("Line 1\nLine 2");
  }

  @Test
  void buildContext_nullVendorDetail_doesNotThrow() throws IOException {
    var order = loadOrder("edifact/acquisitions/composite_purchase_order_email_context.json");
    order.getPoLines().get(0).setVendorDetail(null);

    OrderEmailContext ctx = mapper.buildContext(List.of(order), "text/html");

    OrderLineContext line = ctx.getOrders().get(0).orderLines().get(0).orderLine();
    assertThat(line.getVendorDetail().getInstructions()).isEmpty();
  }

  private CompositePurchaseOrder loadOrder(String path) throws IOException {
    return objectMapper.readValue(getMockData(path), CompositePurchaseOrder.class);
  }
}
