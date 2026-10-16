package com.mx.cbtis.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;

/**
 * Controla los errores al consumir la API REST (servicio caido, 4xx/5xx) para que
 * el frontend reciba una respuesta JSON controlada en lugar de una pagina de error.
 */
@ControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(RestClientException.class)
	public ResponseEntity<Map<String, String>> manejarErrorApi(RestClientException ex) {
		Map<String, String> cuerpo = Map.of(
				"codigo", "API-502",
				"mensaje", "El servicio de datos no esta disponible o devolvio un error");
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(cuerpo);
	}
}
