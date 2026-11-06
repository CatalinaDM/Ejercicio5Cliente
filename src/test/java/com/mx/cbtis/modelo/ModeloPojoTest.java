package com.mx.cbtis.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pruebas de las entidades: cada constructor se invoca con valores de ejemplo y cada
 * par setter/getter debe devolver el mismo valor que se guardo.
 */
class ModeloPojoTest {

    static Stream<Class<?>> entidades() {
        return Stream.of(Asignaciones.class, Comentarios.class, Proyectos.class, Tareas.class, Usuario.class);
    }

    private static Object valorDeEjemplo(Class<?> tipo) {
        if (tipo == Integer.class || tipo == int.class) {
            return 7;
        }
        if (tipo == String.class) {
            return "valor";
        }
        throw new IllegalArgumentException("Tipo no soportado: " + tipo);
    }

    @ParameterizedTest
    @MethodSource("entidades")
    void constructoresCreanLaEntidad(Class<?> entidad) throws Exception {
        for (Constructor<?> ctor : entidad.getConstructors()) {
            Object[] args = Arrays.stream(ctor.getParameterTypes())
                    .map(ModeloPojoTest::valorDeEjemplo).toArray();
            assertNotNull(ctor.newInstance(args));
        }
    }

    @ParameterizedTest
    @MethodSource("entidades")
    void settersYGettersConservanElValor(Class<?> entidad) throws Exception {
        Object instancia = entidad.getConstructor().newInstance();
        int pares = 0;
        for (Method setter : entidad.getDeclaredMethods()) {
            if (!setter.getName().startsWith("set") || !Modifier.isPublic(setter.getModifiers())) {
                continue;
            }
            Method getter = entidad.getMethod("get" + setter.getName().substring(3));
            Object valor = valorDeEjemplo(setter.getParameterTypes()[0]);
            setter.invoke(instancia, valor);
            assertEquals(valor, getter.invoke(instancia), setter.getName());
            pares++;
        }
        assertEquals(true, pares > 0);
    }
}
