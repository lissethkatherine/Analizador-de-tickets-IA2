package com.ejemplo.tickets.service;

import java.text.Normalizer;
import java.util.List;

/**
 * Valores permitidos para categoria, prioridad y area
 * Se usan en dos lugares: en el prompt (para decirle al LLM que opciones tiene)
 * y para validar lo que el LLM responde (nunca confiamos ciegamente en su salida)
 */
// "final" + constructor privado = clase de utilidad, nadie puede crear objetos de ella
// ni heredar de ella solo se usa con Catalogo.algo
public final class Catalogo {

    // Las listas oficiales. Si quiero agregar una categoria nueva, la agrego aca
    // (y tambien en el prompt y el codigo de n8n, que tienen su propia copia)
    public static final List<String> CATEGORIAS = List.of(
            "Facturación", "Soporte técnico", "Envíos", "Cuenta y acceso",
            "Devoluciones", "Seguridad", "Consulta general");

    public static final List<String> PRIORIDADES = List.of("alta", "media", "baja");

    public static final List<String> AREAS = List.of(
            "Finanzas", "TI", "Logística", "Atención al cliente", "Seguridad");

    private Catalogo() { }

    /**
     * Devuelve el valor oficial de la lista que coincide con "valor" sin importar
     * mayusculas ni acentos ("facturacion" -> "Facturación"), o null si no existe
     */
    public static String canonico(String valor, List<String> permitidos) {
        if (valor == null) {
            return null;
        }
        // Normalizo lo que mando el LLM para poder compararlo "parejo"
        String buscado = normalizar(valor);
        // Recorro la lista oficial y comparo version normalizada contra version normalizada
        for (String permitido : permitidos) {
            if (normalizar(permitido).equals(buscado)) {
                // Devuelvo la version OFICIAL (con su tilde y mayuscula), no la del LLM
                return permitido;
            }
        }
        // Si nada coincide, el LLM invento algo fuera del catalogo
        return null;
    }

    // Quita espacios, tildes y mayusculas: " Facturación " -> "facturacion"
    private static String normalizar(String s) {
        // NFD separa la letra de su tilde (á -> a + ´)
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                // \p{M} son las marcas de acento sueltas; las borro
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }
}
