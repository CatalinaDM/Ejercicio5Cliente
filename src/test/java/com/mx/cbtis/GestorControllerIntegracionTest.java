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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.net.httpserver.HttpServer;

/**
 * Pruebas automaticas del frontend (GestorController + vistas Thymeleaf).
 * El backend (API REST) se simula con un servidor HTTP local en un puerto libre
 * asignado por el sistema operativo; ese puerto se inyecta en la propiedad
 * {@code api.port} para que el controlador consuma la API simulada sin depender
 * del valor configurado en application.properties ni de puertos ocupados en CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GestorControllerIntegracionTest {

	/** Puerto 0: el sistema operativo asigna un puerto libre. */
	static final int PUERTO_LIBRE = 0;

	@DynamicPropertySource
	static void configurarPuertoApi(DynamicPropertyRegistry registro) {
		registro.add("api.port", () -> api.getAddress().getPort());
	}

	record Respuesta(int status, String cuerpo) {}

	static HttpServer api;
	static final Map<String, Respuesta> rutas = new ConcurrentHashMap<>();
	static final Map<String, String> recibido = new ConcurrentHashMap<>();

	@Autowired
	MockMvc mockMvc;

	@BeforeAll
	static void levantarApiSimulada() throws IOException {
		api = HttpServer.create(new InetSocketAddress(PUERTO_LIBRE), 0);
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
	@DisplayName("AUT-S01 Seguridad: el registro ignora campos no permitidos (mitigacion CVE-2026-59282)")
	void registrarUsuarioIgnoraCamposNoPermitidos() throws Exception {
		rutas.put("POST /addUsuario", new Respuesta(200, "1"));

		mockMvc.perform(post("/saveUsuario")
						.param("nombre", "Usuario Prueba")
						.param("id_usuario", "99")
						.param("nombre.class.module", "x"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/login"));

		assertThat(recibido.get("POST /addUsuario"))
				.contains("\"nombre\":\"Usuario Prueba\"")
				.doesNotContain("\"id_usuario\":99");
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

	// ------------------------------------------------------------------ RESTO DE ENDPOINTS

	@Test
	@DisplayName("AUT-E05 Vistas estaticas: home, tutorial, manual, login y proyecto responden con su vista")
	void lasVistasEstaticasResponden() throws Exception {
		rutas.put("POST /buscarUsuarios", new Respuesta(200, "[{\"id_usuario\":1,\"nombre\":\"Ana\"}]"));

		mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("home"));
		mockMvc.perform(get("/tutorial")).andExpect(status().isOk()).andExpect(view().name("tutorial"));
		mockMvc.perform(get("/manual")).andExpect(status().isOk()).andExpect(view().name("manual"));
		mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("login"));
		mockMvc.perform(get("/proyecto")).andExpect(status().isOk()).andExpect(view().name("proyecto"))
				.andExpect(model().attributeExists("tarea", "comentarios", "listaUsuarios"));
		mockMvc.perform(get("/tarea")).andExpect(status().isOk()).andExpect(view().name("proyecto"))
				.andExpect(model().attributeExists("tareas"));
	}

	@Test
	@DisplayName("AUT-E06 Proyectos: guardar redirige a /inicio y eliminar reenvia el DELETE al backend")
	void guardarYEliminarProyecto() throws Exception {
		rutas.put("POST /addProyecto", new Respuesta(200, "1"));
		rutas.put("DELETE /deleteProyecto", new Respuesta(200, "1"));

		mockMvc.perform(post("/saveProyecto").param("nombre", "Proyecto A").param("estado_proyecto", "Activo"))
				.andExpect(status().is3xxRedirection()).andExpect(header().string("Location", "/inicio"));
		assertThat(recibido.get("POST /addProyecto")).contains("\"nombre\":\"Proyecto A\"");

		mockMvc.perform(post("/eliminarProyecto").contentType(MediaType.APPLICATION_JSON).content("{\"id_proyecto\":5}"))
				.andExpect(status().is3xxRedirection()).andExpect(header().string("Location", "/inicio"));
		assertThat(recibido.get("DELETE /deleteProyecto")).contains("\"id_proyecto\":5");
	}

	@Test
	@DisplayName("AUT-E07 Consultas: usuarios, tareas y comentarios se devuelven tal como los entrega el backend")
	void consultasDeListas() throws Exception {
		rutas.put("POST /buscarUsuarios", new Respuesta(200, "[{\"id_usuario\":1,\"nombre\":\"Ana\"}]"));
		rutas.put("POST /buscarTareas", new Respuesta(200, "[{\"id_tarea\":2,\"nombre\":\"Tarea 2\"}]"));
		rutas.put("POST /buscarComentarios", new Respuesta(200, "[{\"id_comentario\":3,\"texto_comentario\":\"Hola\"}]"));

		mockMvc.perform(post("/consultarUsuarios")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Ana"));
		mockMvc.perform(post("/consultarTareas")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id_tarea").value(2));
		mockMvc.perform(post("/consultarComentarios")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].texto_comentario").value("Hola"));
	}

	@Test
	@DisplayName("AUT-E08 Tareas y comentarios por proyecto/tarea: se consultan en el backend con el cuerpo recibido")
	void obtenerTareasYComentarios() throws Exception {
		rutas.put("POST /getTarea", new Respuesta(200, "[{\"id_tarea\":2,\"id_proyecto\":9}]"));
		rutas.put("POST /getComentario", new Respuesta(200, "[{\"id_comentario\":3,\"id_tarea\":2}]"));

		mockMvc.perform(post("/obtenerTareas").contentType(MediaType.APPLICATION_JSON).content("{\"id_proyecto\":9}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id_tarea").value(2));
		mockMvc.perform(post("/obtenerComentarios").contentType(MediaType.APPLICATION_JSON).content("{\"id_tarea\":2}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id_comentario").value(3));
	}

	@Test
	@DisplayName("AUT-E09 Tareas: guardar redirige a /proyecto, y verificar/cambiar/eliminar/actualizar usan el backend")
	void operacionesDeTareas() throws Exception {
		rutas.put("POST /addTareas", new Respuesta(200, "1"));
		rutas.put("DELETE /deleteTareas", new Respuesta(200, "1"));
		rutas.put("GET /buscarTarea?id_tarea=4", new Respuesta(200, "{\"id_tarea\":4,\"nombre\":\"Tarea 4\"}"));

		mockMvc.perform(post("/saveTarea").param("nombre", "Tarea 4").param("id_proyecto", "9"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/verificarTarea").contentType(MediaType.APPLICATION_JSON).content("{\"id_tarea\":4}"))
				.andExpect(status().isOk()).andExpect(content().string("1"));
		mockMvc.perform(post("/cambiosTareas").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"Hecha\"}"))
				.andExpect(status().isOk()).andExpect(content().string("1"));
		mockMvc.perform(post("/actualizarTarea").contentType(MediaType.APPLICATION_JSON).content("{\"id_tarea\":4}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Tarea 4"));
		mockMvc.perform(post("/eliminarTarea").contentType(MediaType.APPLICATION_JSON).content("{\"id_tarea\":4}"))
				.andExpect(status().is3xxRedirection()).andExpect(header().string("Location", "/proyecto"));
	}

	@Test
	@DisplayName("AUT-E10 Comentarios: guardar un comentario devuelve el resultado del backend")
	void guardarComentario() throws Exception {
		rutas.put("POST /addComentario", new Respuesta(200, "1"));

		mockMvc.perform(post("/saveComentario").contentType(MediaType.APPLICATION_JSON)
						.content("{\"id_tarea\":2,\"texto_comentario\":\"Hola\"}"))
				.andExpect(status().isOk()).andExpect(content().string("1"));
	}
}
