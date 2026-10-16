package com.mx.cbtis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.net.httpserver.HttpServer;

/**
 * Pruebas automaticas del frontend (GestorController + vistas Thymeleaf).
 * El backend (API REST) se simula con un servidor HTTP local en el mismo puerto
 * que usa el controlador, de modo que se prueba el consumo real de los endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GestorControllerIntegracionTest {

	/** Puerto en el que el controlador espera a la API (8081 por defecto). */
	static final int PUERTO_API = Integer.getInteger("api.port", 8081);

	record Respuesta(int status, String cuerpo) {}

	static HttpServer api;
	static final Map<String, Respuesta> rutas = new ConcurrentHashMap<>();
	static final Map<String, String> recibido = new ConcurrentHashMap<>();

	@Autowired
	MockMvc mockMvc;

	@BeforeAll
	static void levantarApiSimulada() throws IOException {
		api = HttpServer.create(new InetSocketAddress(PUERTO_API), 0);
		api.createContext("/", ex -> {
			String clave = ex.getRequestMethod() + " " + ex.getRequestURI();
			recibido.put(clave, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			Respuesta r = rutas.getOrDefault(clave, new Respuesta(404, "{}"));
			byte[] bytes = r.cuerpo().getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(r.status(), bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) ex.getResponseBody().write(bytes);
			ex.close();
		});
		api.start();
	}

	@AfterAll
	static void detenerApiSimulada() {
		api.stop(0);
	}

	@BeforeEach
	void limpiar() {
		rutas.clear();
		recibido.clear();
	}

	// ------------------------------------------------------------------ CASOS DE EXITO

	@Test
	@DisplayName("AUT-E01 Servicio de consulta: el backend devuelve proyectos validos y el frontend los procesa")
	void consultarProyectosProcesaLaRespuestaDelBackend() throws Exception {
		rutas.put("POST /buscarProyectos", new Respuesta(200,
				"[{\"id_proyecto\":197,\"nombre\":\"My API Rest\",\"estado_proyecto\":\"En proceso\"}]"));

		mockMvc.perform(post("/consultarProyectos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id_proyecto").value(197))
				.andExpect(jsonPath("$[0].nombre").value("My API Rest"));
	}

	@Test
	@DisplayName("AUT-E02 Formulario: datos validos de registro se envian al endpoint /addUsuario y redirige a /login")
	void registrarUsuarioConDatosValidosEnviaElFormularioAlBackend() throws Exception {
		rutas.put("POST /addUsuario", new Respuesta(200, "1"));

		mockMvc.perform(post("/saveUsuario")
						.param("nombre", "Usuario Prueba")
						.param("correo_electronico", "prueba@cbtis75.edu.mx")
						.param("contrasena", "prueba123")
						.param("rol", "Estudiante"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/login"));

		assertThat(recibido.get("POST /addUsuario"))
				.contains("\"nombre\":\"Usuario Prueba\"")
				.contains("\"correo_electronico\":\"prueba@cbtis75.edu.mx\"");
	}

	@Test
	@DisplayName("AUT-E03 Vista: /inicio entrega la vista con el modelo de proyectos y /register con el modelo de usuario")
	void lasVistasSeRenderizanConSuModelo() throws Exception {
		mockMvc.perform(get("/inicio"))
				.andExpect(status().isOk())
				.andExpect(view().name("inicio"))
				.andExpect(model().attributeExists("proyectos"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("cardContainer")));

		mockMvc.perform(get("/register"))
				.andExpect(status().isOk())
				.andExpect(view().name("register"))
				.andExpect(model().attributeExists("usuario"));
	}

	@Test
	@DisplayName("AUT-E04 Login: credenciales validas devuelven el usuario autenticado")
	void verificarLoginDevuelveElUsuario() throws Exception {
		rutas.put("POST /getUsuarioLogin", new Respuesta(200,
				"{\"id_usuario\":131,\"nombre\":\"Ana\",\"correo_electronico\":\"ana@cbtis75.edu.mx\"}"));

		mockMvc.perform(post("/verificarLogin").contentType(MediaType.APPLICATION_JSON)
						.content("{\"correo_electronico\":\"ana@cbtis75.edu.mx\",\"contrasena\":\"0722\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana"));
	}

	// ------------------------------------------------------------------ CASOS DE FRACASO

	@Test
	@DisplayName("AUT-F01 Servicio de consulta: si el backend devuelve error, el frontend lo captura y lo controla")
	void consultarProyectosControlaElErrorDelBackend() throws Exception {
		rutas.put("POST /buscarProyectos", new Respuesta(500, "{\"codigo\":\"SYS-999\"}"));

		mockMvc.perform(post("/consultarProyectos"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.codigo").value("API-502"))
				.andExpect(jsonPath("$.mensaje").exists());
	}

	@Test
	@DisplayName("AUT-F02 Formulario: el formulario de registro declara campos obligatorios y tipo email (impide enviar datos incompletos)")
	void formularioDeRegistroExigeCamposObligatorios() throws Exception {
		String html = mockMvc.perform(get("/register")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(html).contains("novalidate").contains("needs-validation");
		assertThat(etiquetaInput(html, "name=\"nombre\"")).contains("required");
		assertThat(etiquetaInput(html, "type=\"email\"")).contains("required");
		assertThat(etiquetaInput(html, "type=\"password\"")).contains("required");
	}

	/** Devuelve la etiqueta &lt;input ...&gt; completa que contiene el fragmento indicado. */
	private static String etiquetaInput(String html, String fragmento) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("<input[^>]*" + java.util.regex.Pattern.quote(fragmento) + "[^>]*>").matcher(html);
		assertThat(m.find()).as("input con " + fragmento).isTrue();
		return m.group();
	}

	@Test
	@DisplayName("AUT-F03 Vista: ruta inexistente responde 404 y proyecto inexistente devuelve un objeto vacio controlado")
	void recursoInexistenteMantieneEstadoControlado() throws Exception {
		mockMvc.perform(get("/noexiste")).andExpect(status().isNotFound());

		rutas.put("GET /buscarProyecto?id_proyecto=99999", new Respuesta(200, "{\"id_proyecto\":0}"));
		mockMvc.perform(post("/actualizarProyecto").contentType(MediaType.APPLICATION_JSON)
						.content("{\"id_proyecto\":99999}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id_proyecto").value(0))
				.andExpect(jsonPath("$.nombre").doesNotExist());
	}

	@Test
	@DisplayName("AUT-F04 Login: credenciales incorrectas devuelven el marcador '000' que la interfaz traduce en mensaje de error")
	void verificarLoginConCredencialesInvalidas() throws Exception {
		rutas.put("POST /getUsuarioLogin", new Respuesta(200, "{\"nombre\":\"000\"}"));

		mockMvc.perform(post("/verificarLogin").contentType(MediaType.APPLICATION_JSON)
						.content("{\"correo_electronico\":\"ana@cbtis75.edu.mx\",\"contrasena\":\"mala\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("000"));
	}

	@Test
	@DisplayName("AUT-F05 Login: si el backend no responde, el frontend devuelve un error controlado (no una excepcion)")
	void verificarLoginControlaApiNoDisponible() throws Exception {
		// sin ruta registrada el servidor simulado responde 404 -> equivalente a un recurso/servicio no disponible
		mockMvc.perform(post("/verificarLogin").contentType(MediaType.APPLICATION_JSON)
						.content("{\"correo_electronico\":\"a@b.c\",\"contrasena\":\"x\"}"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.codigo").value("API-502"));
	}
}
