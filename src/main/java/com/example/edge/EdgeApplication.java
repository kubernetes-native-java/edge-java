package com.example.edge;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class EdgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(EdgeApplication.class, args);
	}

	@Bean
	RSocketRequester rSocketRequester(
		@Value("${crm.orders-uri}") URI ordersUri, RSocketRequester.Builder builder) {
		return builder.tcp(ordersUri.getHost(), ordersUri.getPort());
	}

	@Bean
	WebClient webClient(WebClient.Builder builder) {
		return builder.build();
	}

	@Bean
	RouteLocator gateway(
		@Value("${crm.customers-uri}") URI customersUri,
		RouteLocatorBuilder rlb) {
		return rlb
			.routes()
			.route(rs ->
				rs.path("/proxy")
					.filters(fs -> fs
						.setPath("/customers").addResponseHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
					)
					.uri(customersUri)
			)
			.build();
	}
}


@Controller
class CrmGraphqlController {

	CrmGraphqlController(CrmClient crm) {
		this.crm = crm;
	}

	@QueryMapping
	Flux<Customer> customers() {
		return this.crm.getCustomers();
	}

	@SchemaMapping(typeName = "Customer")
	Flux<Order> orders(Customer customer) {
		return this.crm.getOrdersFor(customer.id());
	}

	private final CrmClient crm;
}

@Component
class CrmClient {


	private final WebClient http;

	private final RSocketRequester rSocket;

	private final URI customersUri;

	private final Log log = LogFactory.getLog(getClass());

	CrmClient(
		@Value("${crm.customers-uri}") URI customersUri,
		WebClient http, RSocketRequester rSocket) {
		this.http = http;
		this.rSocket = rSocket;
		this.customersUri = URI.create(customersUri.getScheme() + "://"
			+ customersUri.getHost() + ":" + customersUri.getPort() + "/customers");
		log.info("the customers service is " + this.customersUri);

	}


	Flux<Order> getOrdersFor(Integer customerId) {
		return rSocket
			.route("orders.{cid}", customerId)
			.retrieveFlux(Order.class);
	}

	Flux<Customer> getCustomers() {
		return this.http.get()
			.uri(this.customersUri)
			.retrieve()
			.bodyToFlux(Customer.class);
	}


	Flux<CustomerOrders> getCustomerOrders() {
		return getCustomers()
			.flatMap(c -> Mono.zip(Mono.just(c), getOrdersFor(c.id()).collectList()))
			.map(tuple2 -> new CustomerOrders(tuple2.getT1(), tuple2.getT2()));
	}

}

@Controller
@ResponseBody
class CrmRestController {

	private final CrmClient crm;

	CrmRestController(CrmClient crm) {
		this.crm = crm;
	}

	@GetMapping("/cos")
	Flux<CustomerOrders> getCustomerOrders() {
		return this.crm.getCustomerOrders();
	}
}


record CustomerOrders(Customer customer, List<Order> orders) {
}

record Customer(Integer id, String name) {
}

record Order(Integer id, Integer customerId) {
}