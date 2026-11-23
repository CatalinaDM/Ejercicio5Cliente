package com.mx.cbtis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/** Arranca la aplicacion por su metodo main (puerto aleatorio) para cubrir el punto de entrada. */
class Ejercicio5ClienteMainTest {

	@Test
	void mainArrancaLaAplicacion() {
		assertDoesNotThrow(() -> Ejercicio5ClienteApplication.main(new String[] { "--server.port=0" }));
	}
}
