package cl.duoc.digitalfix.rabbitadmin.web.dto;

import java.util.Map;

/**
 * Lo que hizo POST /queues/{dlq}/reprocess: cuantos mensajes volvieron a su
 * cola original (por cola) y cuantos quedaron en la DLQ porque no se supo a
 * donde devolverlos.
 */
public record ResultadoReproceso(
        String dlq,
        int reprocesados,
        int sinDestino,
        Map<String, Integer> porDestino
) {}
