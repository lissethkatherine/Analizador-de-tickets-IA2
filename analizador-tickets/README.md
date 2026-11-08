# Clasificador y priorizador de tickets de soporte (n8n + Spring Boot + RAG)

Proyecto con **n8n + Spring Boot + Embeddings + Qdrant + LLM**. 
El usuario escribe un ticket en texto libre y el sistema devuelve un **análisis estructurado** (no es un chatbot): categoría, prioridad, área sugerida, tiempo estimado de resolución, confianza, justificación y los tickets históricos que respaldan la predicción.

## 1. Arquitectura

```
Navegador (frontend)
    |  POST /analizar
    v
Spring Boot  ------------------------------>  PostgreSQL (historial)
    |  POST /webhook/analizar-ticket
    v
n8n (automatización)
    |-- Qdrant: si faltan los tickets históricos, los carga solo (automático)
    |-- OpenRouter: embedding del ticket
    |-- Qdrant: busca los 5 tickets históricos más parecidos
    |-- Filtra por umbral de similitud y arma el contexto (RAG)
    |-- OpenRouter: LLM clasifica y responde en JSON
    '-- Valida el JSON y responde a Spring Boot
```

**Quién hace qué**
- **n8n** hace todo el RAG (igual que en tu ejercicio de búsqueda semántica de cursos): embeddings, Qdrant, prompt y LLM. También carga solo los tickets históricos en Qdrant la primera vez (no hay pasos manuales en n8n).
- **Spring Boot** es la puerta de entrada: valida la petición, llama al webhook de n8n, vuelve a validar la respuesta, guarda el historial en PostgreSQL y sirve el frontend.
- **La clave de OpenRouter vive solo en n8n** (credencial). Spring Boot no necesita ninguna clave de IA.

## 2. Requisitos

- Java 21+ y Gradle 8.x (o tu IDE: IntelliJ y VS Code lo configuran solos al abrir la carpeta)
- Docker Desktop
- Una API key **nueva** de OpenRouter (https://openrouter.ai/keys)

## 3. Puesta en marcha

1. **Variables:** copia `.env.example` como `.env` y cambia `DB_PASSWORD`.
2. **Infraestructura:** `docker compose up -d` (levanta n8n en http://localhost:5678, Qdrant y PostgreSQL).
3. **Importa el workflow en n8n:** crea tu cuenta local en n8n, luego *Workflows → Import from file* y elige `n8n/workflow_n8n_tickets.json`.
4. **Crea la credencial de OpenRouter:** en n8n, *Credentials → Create → Bearer Auth*, nombre "OpenRouter" y en *Token* pega tu clave nueva (solo la clave, sin la palabra "Bearer"). Luego abre estos 3 nodos y selecciona esa credencial en *Credential for Bearer Auth*:
   - `Generar Embeddings`
   - `Embedding Ticket`
   - `LLM Clasificar`
5. **Activa el workflow:** interruptor **Active** (arriba a la derecha). Esto habilita la URL de producción `/webhook/analizar-ticket`. No hay nada más que ejecutar a mano en n8n.
6. **Inicia el backend:** `gradle bootRun`
   (Si tienes el wrapper del proyecto, usa `./gradlew bootRun`; en Windows `gradlew.bat bootRun`.
   Si no existe, créalo una sola vez con `gradle wrapper --gradle-version 8.10.2`.
   En IntelliJ basta con abrir la carpeta y ejecutar la tarea `bootRun` o la clase `TicketsApplication`.)
7. **Abre el frontend:** http://localhost:8081 y analiza un ticket.

> **Primera consulta:** n8n detecta que Qdrant todavía no tiene la colección `tickets_historicos`, genera los embeddings de los 33 tickets y los guarda. Por eso la primera respuesta tarda unos segundos más. Las siguientes son rápidas.

Comprobación: `GET http://localhost:8081/salud` debe mostrar `ok` en api, baseDatos y n8n.

## 4. Endpoints de Spring Boot

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/analizar` | Analiza un ticket. Cuerpo: `{"texto": "..."}` |
| GET | `/historial` | Últimos 20 análisis guardados |
| GET | `/salud` | Estado de la API, la base de datos y n8n |

Ejemplo:
```
curl -X POST http://localhost:8081/analizar -H "Content-Type: application/json" \
  -d '{"texto":"Me cobraron dos veces el pedido 4521 y necesito el reembolso hoy."}'
```
Respuesta (resumida):
```json
{
  "texto": "Me cobraron dos veces...",
  "analisis": {
    "categoria": "Facturación", "prioridad": "alta", "area_sugerida": "Finanzas",
    "tiempo_estimado_horas": 24, "confianza": 0.86,
    "justificacion": "...", "casos_usados": [1, 4]
  },
  "similares": [ { "id": 1, "texto": "...", "similitud_porcentaje": 71.4, "...": "..." } ],
  "modelo": "openai/gpt-4.1-mini",
  "mensaje": null
}
```
Si ningún ticket histórico se parece lo suficiente, `analisis` viene `null` y `mensaje` explica que no hay información suficiente (no se llama al LLM).

Códigos de error: `400` entrada inválida, `502` n8n caído, workflow inactivo o respuesta inválida.

## 5. El workflow de n8n (19 nodos, un solo flujo automático)

Todo arranca con el **Webhook**; no hay disparador manual. El nodo **Respond to Webhook** devuelve el resultado a Spring Boot apenas el análisis está listo.

`Webhook → Preparar ticket → Comprobar Coleccion → ¿Existe coleccion?`
- **Sí existe:** `→ Embedding Ticket` (sigue directo).
- **No existe (solo la primera vez):** carga automática `→ Preparar Tickets → Generar Embeddings → Preparar puntos → Borrar Coleccion → Crear Coleccion → Guardar en Qdrant →` y continúa con `Embedding Ticket`.

Luego, para todos los casos:
`Embedding Ticket → Preparar Consulta → Buscar en Qdrant → Preparar Contexto RAG → If`
- Si hay contexto: `→ LLM Clasificar → Construir Respuesta → Respond to Webhook`
- Si no hay casos: `→ Responder Sin Casos → Respond to Webhook`

Qué hace cada nodo clave:
- **Preparar ticket:** valida que el texto tenga entre 10 y 2000 caracteres.
- **Comprobar Coleccion / ¿Existe coleccion?:** preguntan a Qdrant si `tickets_historicos` ya existe; si no, disparan la carga automática.
- **Preparar Contexto RAG:** descarta casos con similitud menor a 0.35 y arma el prompt (el ticket y los casos se tratan como datos, no como instrucciones).
- **LLM Clasificar:** temperatura 0.2 y respuesta solo en JSON.
- **Construir Respuesta:** valida categoría, prioridad y área contra las listas permitidas (ignora mayúsculas y acentos), limita la confianza entre 0 y 1 y descarta ids de casos que no se le entregaron al modelo.

Notas:
- Si tu n8n corre fuera de Docker, cambia `http://host.docker.internal:6333` por `http://localhost:6333` en los nodos `Preparar Tickets` y `Preparar ticket`.
- Para ajustar el umbral edita la constante `UMBRAL` en `Preparar Contexto RAG`.
- Para agregar o cambiar tickets, edita el arreglo en `Preparar Tickets` (y `n8n/tickets_historicos.json` como respaldo) y borra la colección: `curl -X DELETE http://localhost:6333/collections/tickets_historicos`. El siguiente ticket que llegue la vuelve a crear con los datos nuevos.
- Si dos tickets llegan exactamente a la vez en la primera ejecución, ambos pueden intentar cargar; el resultado es el mismo porque la carga borra y recrea la colección.

## 6. Estructura del proyecto

```
analizador-tickets/
├── build.gradle                 dependencias de Gradle
├── settings.gradle
├── docker-compose.yml           n8n + Qdrant + PostgreSQL
├── .env.example
├── n8n/
│   ├── workflow_n8n_tickets.json   workflow para importar en n8n
│   └── tickets_historicos.json     33 tickets de ejemplo (respaldo)
└── src/main/
    ├── java/com/ejemplo/tickets/
    │   ├── config/        AppProperties, ClientesConfig (cliente de n8n), CorsConfig
    │   ├── controller/    AnalisisController (/analizar, /historial), SaludController
    │   ├── service/       AnalisisService (flujo), N8nService (llama a n8n), Catalogo
    │   ├── model/         AnalisisTicket (entidad del historial)
    │   ├── repository/    AnalisisTicketRepository
    │   ├── dto/           objetos de entrada/salida
    │   └── exception/     errores -> { "error": "..." }
    └── resources/
        ├── application.properties
        └── static/        frontend (index.html, styles.css, app.js)
```

## 7. Casos de prueba

1. "Me cobraron dos veces el pedido 4521..." -> Facturación, alta, Finanzas.
2. "No puedo entrar a mi cuenta..." -> Cuenta y acceso, media, TI.
3. "Veo compras en mi tarjeta que yo no hice..." -> Seguridad, alta, Seguridad.
4. Ticket mixto (cobro duplicado y producto que no llegó) -> cita casos de dos categorías y baja la confianza.
5. "¿Cuál es la capital de Francia?" -> sin predicción, aviso de información insuficiente.
6. Texto muy corto -> el frontend avisa y la API responde 400.

## 8. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `n8n respondió 404` | El workflow no está activo, o usas `/webhook-test/`. Actívalo y usa `/webhook/analizar-ticket`. |
| `No se pudo conectar con n8n` | n8n apagado o puerto incorrecto. `docker compose up -d` y revisa `N8N_WEBHOOK_URL` en `.env`. |
| `n8n respondió con error 500` | Abre *Executions* en n8n y mira el nodo en rojo: normalmente es la credencial de OpenRouter sin asignar o Qdrant sin responder. |
| Nodo `Embedding`/`LLM` con error 401 | Credencial sin asignar o clave incorrecta/revocada. |
| Nodo `Comprobar Coleccion` o `Buscar en Qdrant` falla | Qdrant apagado o URL no alcanzable desde n8n (revisa `host.docker.internal`). |
| Muchos tickets caen en "información insuficiente" | Baja `UMBRAL` a 0.25 en `Preparar Contexto RAG`, o agrega más tickets. |
| Error de base de datos | Revisa que `DB_*` del `.env` coincidan con lo que usó Docker la primera vez. Si cambiaste la clave: `docker compose down -v`. |

## 9. Mejoras futuras

- Login con JWT y tickets por usuario
- Carga de tickets desde CSV en lote
- Botón de retroalimentación para añadir tickets corregidos a Qdrant desde n8n
- Panel de estadísticas por categoría y área
