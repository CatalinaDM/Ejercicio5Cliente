package com.mx.cbtis;

import org.junit.jupiter.api.Test;

/** Arranca la aplicacion por su metodo main (puerto aleatorio) para cubrir el punto de entrada. */
class Ejercicio5ClienteMainTest {

	@Test
	void mainArrancaLaAplicacion() {
		Ejercicio5ClienteApplication.main(new String[] { "--server.port=0" });
	}
}
