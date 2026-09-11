package com.osmar.boutiqueos.settings;

import com.jayway.jsonpath.JsonPath;
import com.osmar.boutiqueos.customer.Customer;
import com.osmar.boutiqueos.customer.CustomerRepository;
import com.osmar.boutiqueos.product.Product;
import com.osmar.boutiqueos.product.ProductRepository;
import com.osmar.boutiqueos.sale.PaymentMethod;
import com.osmar.boutiqueos.sale.Sale;
import com.osmar.boutiqueos.sale.SaleItem;
import com.osmar.boutiqueos.sale.SaleRepository;
import com.osmar.boutiqueos.sale.SaleStatus;
import com.osmar.boutiqueos.subscription.AccountSubscription;
import com.osmar.boutiqueos.subscription.AccountSubscriptionRepository;
import com.osmar.boutiqueos.subscription.PlanType;
import com.osmar.boutiqueos.subscription.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El respaldo recorriendo el camino real: descargar el JSON por HTTP y volverlo
 * a subir tal cual.
 *
 * <p>{@link BackupServiceTests} llama al servicio con objetos Java y se salta la
 * conversion a JSON. Aqui se prueba justo esa parte: que lo que el endpoint
 * escribe lo pueda volver a leer. Spring Boot 4 serializa con Jackson 3 aunque
 * el proyecto declare Jackson 2, asi que no basta con probar con un
 * ObjectMapper cualquiera.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BackupEndpointTests {

    private static final String PASSWORD = "contrasenaDeRespaldo123";
    private static final String STORE = "Tienda Ida y Vuelta";
    private static final String SESSION = AuthSessionService.SESSION_HEADER;

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthSessionService authSessionService;
    @Autowired private AppSettingsService appSettingsService;
    @Autowired private AccountSubscriptionRepository subscriptionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SaleRepository saleRepository;

    private Long accountId;
    private String token;

    @BeforeEach
    void cuentaConPlanPro() throws Exception {
        String username = "ida-vuelta-" + UUID.randomUUID() + "@boutique.test";
        accountId = appSettingsService.provisionOwner(username, PASSWORD, STORE).getId();

        // El respaldo es funcion de plan: sin PRO el endpoint responde 403.
        AccountSubscription sub = subscriptionRepository.findByAccountId(accountId)
                .orElseGet(AccountSubscription::new);
        sub.setAccountId(accountId);
        sub.setPlan(PlanType.PRO);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);

        // La sesion se crea directo: el login por HTTP ahora pide un codigo por
        // correo (eso se prueba en TwoFactorAuthTests); aqui importa el respaldo.
        token = authSessionService.createSession(accountId);
    }

    @Test
    void elJsonDescargadoSeRestauraTalCual() throws Exception {
        Customer cliente = guardarCliente("Ana");
        Product vestido = guardarProducto("Vestido");
        Instant cuando = Instant.now().minus(9, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        guardarVenta(vestido, cliente, cuando);

        String respaldo = descargarRespaldo();

        // Algo que se crea DESPUES del respaldo tiene que desaparecer al
        // restaurar: restaurar reemplaza, no mezcla.
        guardarProducto("Creado despues del respaldo");

        mockMvc.perform(post("/api/backup/restore")
                        .header(SESSION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"" + STORE + "\",\"backup\":" + respaldo + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restored").value(true))
                .andExpect(jsonPath("$.counts.products").value(1))
                .andExpect(jsonPath("$.counts.customers").value(1))
                .andExpect(jsonPath("$.counts.sales").value(1));

        List<Product> productos = productRepository.findAllByAccountId(accountId);
        assertEquals(1, productos.size());
        assertEquals("Vestido", productos.getFirst().getName());

        Sale venta = saleRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).getFirst();
        assertEquals(cuando, venta.getCreatedAt());

        SaleItem linea = venta.getItems().getFirst();
        assertEquals(0, new BigDecimal("300").compareTo(linea.getUnitCost()));
        assertEquals(productos.getFirst().getId(), linea.getProductId());
        assertEquals(
                customerRepository.findAllByAccountId(accountId).getFirst().getId(),
                venta.getCustomerId());
    }

    @Test
    void conLaConfirmacionEquivocadaNoSeBorraNada() throws Exception {
        guardarProducto("Blusa");
        String respaldo = descargarRespaldo();
        guardarProducto("Falda");

        mockMvc.perform(post("/api/backup/restore")
                        .header(SESSION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"otra tienda\",\"backup\":" + respaldo + "}"))
                .andExpect(status().isBadRequest());

        assertEquals(2, productRepository.findAllByAccountId(accountId).size());
    }

    @Test
    void sinSesionNoSePuedeRestaurar() throws Exception {
        guardarProducto("Blusa");
        String respaldo = descargarRespaldo();

        mockMvc.perform(post("/api/backup/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"" + STORE + "\",\"backup\":" + respaldo + "}"))
                .andExpect(status().isUnauthorized());
    }

    private String descargarRespaldo() throws Exception {
        return mockMvc.perform(get("/api/backup").header(SESSION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Product guardarProducto(String nombre) {
        Product p = new Product();
        p.setAccountId(accountId);
        p.setName(nombre);
        p.setCostPrice(new BigDecimal("300"));
        p.setSalePrice(new BigDecimal("700"));
        p.setStock(4);
        return productRepository.save(p);
    }

    private Customer guardarCliente(String nombre) {
        Customer c = new Customer();
        c.setAccountId(accountId);
        c.setName(nombre);
        c.setPhone("8180000000");
        return customerRepository.save(c);
    }

    private void guardarVenta(Product producto, Customer cliente, Instant cuando) {
        Sale venta = new Sale();
        venta.setAccountId(accountId);
        venta.setPaymentMethod(PaymentMethod.CASH);
        venta.setStatus(SaleStatus.CONFIRMED);
        venta.setSubtotal(new BigDecimal("700"));
        venta.setTotal(new BigDecimal("700"));
        venta.setEstimatedProfit(new BigDecimal("400"));
        venta.setCustomerId(cliente.getId());
        venta.setCustomerName(cliente.getName());
        venta.setCreatedAt(cuando);

        SaleItem linea = new SaleItem();
        linea.setSale(venta);
        linea.setProductId(producto.getId());
        linea.setProductName(producto.getName());
        linea.setQuantity(1);
        linea.setUnitPrice(new BigDecimal("700"));
        linea.setUnitCost(new BigDecimal("300"));
        linea.setLineTotal(new BigDecimal("700"));
        venta.getItems().add(linea);

        saleRepository.save(venta);
    }
}
