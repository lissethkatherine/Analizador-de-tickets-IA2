# 🎫 Analizador de tickets de soporte (n8n + Spring Boot + RAG)

Proyecto que clasifica y prioriza tickets de soporte usando **IA con RAG**. Escribes el reclamo de un cliente en texto libre y el sistema responde con un **análisis estructurado** (no es un chatbot):

- Categoría
- Prioridad (alta / media / baja)
- Área sugerida
- Tiempo estimado de resolución
- Nivel de confianza
- Justificación del modelo
- Los tickets históricos parecidos que respaldan la predicción

> **¿Qué es RAG?** Antes de preguntarle al LLM, buscamos en una base de datos los tickets históricos más parecidos y se los pasamos como contexto. Así el modelo responde basándose en casos reales y no inventa.

---

## 🧰 Tecnologías

| Tecnología | Para qué se usa |
|---|---|
| **Java 21 + Spring Boot 3.3** | Backend / API REST |
| **PostgreSQL** | Guardar el historial de análisis |
| **n8n** | Automatización que ejecuta el RAG |
| **Qdrant** | Base de datos vectorial (búsqueda por similitud) |
| **OpenRouter** | Acceso al modelo de embeddings y al LLM (`gpt-4.1-mini`) |
| **Docker Compose** | Levantar n8n, Qdrant, PostgreSQL y ngrok |
| **ngrok** | Dominio público para n8n |
| **HTML + CSS + JS** | Frontend simple (sin frameworks) |
| **Lombok** | Menos código repetitivo en las entidades |

---

## 🏗️ Arquitectura

```
Navegador (frontend)
    |  POST /analizar
    v
Spring Boot  ------------------------------>  PostgreSQL (historial)
    |  POST /webhook/analizar-ticket
    v
n8n (automatización)
    |-- Qdrant: si faltan los tickets históricos, los carga solo
    |-- OpenRouter: embedding del ticket
    |-- Qdrant: busca los 5 tickets más parecidos
    |-- Filtra por umbral de similitud y arma el contexto (RAG)
    |-- OpenRouter: el LLM clasifica y responde en JSON
    '-- Valida el JSON y responde a Spring Boot
```

**Quién hace qué**

- **n8n** hace todo el RAG: embeddings, búsqueda en Qdrant, armado del prompt y consulta al LLM. También carga solos los tickets históricos en Qdrant la primera vez.
- **Spring Boot** es la puerta de entrada: valida la petición, llama a n8n, **vuelve a validar** la respuesta (nunca confío ciegamente en lo que dice el LLM), guarda el historial en PostgreSQL y sirve el frontend.
- **La clave de OpenRouter vive solo en n8n** (como credencial). Spring Boot no necesita ninguna clave de IA.

---

## 📋 Requisitos

- Java 21 o superior
- Docker Desktop
- Una cuenta en [OpenRouter](https://openrouter.ai/keys) con una API key
- Una cuenta en [ngrok](https://ngrok.com) (gratis) con su *authtoken* y un dominio estático

---

## 🚀 Puesta en marcha

1. **Entra a la carpeta del proyecto**
   ```bash
   cd analizador-tickets
   ```

2. **Crea el archivo `.env`** dentro de `analizador-tickets/` con tus datos:
   ```properties
   NGROK_AUTHTOKEN=tu_token_de_ngrok
   N8N_WEBHOOK_URL=https://TU-DOMINIO.ngrok-free.dev/webhook/analizar-ticket
   DB_NAME=tickets
   DB_USER=tickets
   DB_PASSWORD=tickets
   ```
   > ⚠️ Nunca subas el `.env` a GitHub (agrégalo al `.gitignore`).

3. **Cambia el dominio de ngrok** en `docker-compose.yml`: el dominio `germinate-had-heroism.ngrok-free.dev` aparece varias veces (servicio `n8n` y servicio `ngrok`). Reemplázalo por el tuyo.

4. **Levanta la infraestructura:**
   ```bash
   docker compose up -d
   ```
   Esto inicia n8n (http://localhost:5678), Qdrant (puerto 6333), PostgreSQL (puerto 5433) y el túnel de ngrok.

5. **Importa el workflow en n8n:** crea tu cuenta local en n8n y luego *Workflows → Import from file* y elige `Workflow/workflow_n8n_tickets.json` (está en la carpeta raíz del repo).

6. **Crea la credencial de OpenRouter** en n8n: *Credentials → Create → Bearer Auth*, ponle de nombre "OpenRouter" y en *Token* pega tu clave (solo la clave, sin la palabra "Bearer"). Después abre estos 3 nodos y selecciona esa credencial:
   - `Generar Embeddings`
   - `Embedding Ticket`
   - `LLM Clasificar`

7. **Activa el workflow** con el interruptor **Active** (arriba a la derecha). Esto habilita la URL de producción `/webhook/analizar-ticket`.

8. **Inicia el backend:**
   ```bash
   ./gradlew bootRun          # Mac / Linux
   gradlew.bat bootRun        # Windows
   ```
   (También puedes abrir la carpeta en IntelliJ y ejecutar la clase `TicketsApplication`.)

9. **Abre el frontend:** http://localhost:8081 y prueba un ticket 🎉

> **Primera consulta:** n8n detecta que Qdrant todavía no tiene la colección `tickets_historicos`, genera los embeddings de los 33 tickets de ejemplo y los guarda. Por eso la primera respuesta tarda unos segundos más. Las siguientes son rápidas.

**Comprobar que todo funciona:** `GET http://localhost:8081/salud` debe mostrar `ok` en `api`, `baseDatos` y `n8n`.

---

## 🔌 Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/analizar` | Analiza un ticket. Cuerpo: `{"texto": "..."}` |
| GET | `/historial` | Últimos 20 análisis guardados |
| GET | `/salud` | Estado de la API, la base de datos y n8n |

**Ejemplo:**
```bash
curl -X POST http://localhost:8081/analizar \
  -H "Content-Type: application/json" \
  -d '{"texto":"Me cobraron dos veces el pedido 4521 y necesito el reembolso hoy."}'
```

**Respuesta (resumida):**
```json
{
  "texto": "Me cobraron dos veces...",
  "analisis": {
    "categoria": "Facturación",
    "prioridad": "alta",
    "area_sugerida": "Finanzas",
    "tiempo_estimado_horas": 24,
    "confianza": 0.86,
    "justificacion": "...",
    "casos_usados": [1, 4]
  },
  "similares": [
    { "id": 1, "texto": "...", "similitud_porcentaje": 71.4 }
  ],
  "modelo": "openai/gpt-4.1-mini",
  "mensaje": null
}
```

Si ningún ticket histórico se parece lo suficiente, `analisis` viene `null` y `mensaje` explica que no hay información suficiente (en ese caso **no se llama al LLM**).

**Códigos de error** (siempre con el formato `{ "error": "..." }`):

- `400` → entrada inválida (texto vacío, muy corto o muy largo)
- `502` → n8n caído, workflow inactivo o respuesta inválida del modelo
- `500` → error inesperado del servidor

---

## 🔄 El workflow de n8n

Todo arranca con el **Webhook** y termina con **Respond to Webhook**, que devuelve el resultado a Spring Boot.

`Webhook → Preparar ticket → Comprobar Coleccion → ¿Existe coleccion?`

- **Si existe:** sigue directo a `Embedding Ticket`.
- **Si no existe (solo la primera vez):** carga automática `→ Preparar Tickets → Generar Embeddings → Preparar puntos → Borrar Coleccion → Crear Coleccion → Guardar en Qdrant` y luego continúa.

Después, en todos los casos:

`Embedding Ticket → Preparar Consulta → Buscar en Qdrant → Preparar Contexto RAG → If`

- **Si hay contexto:** `→ LLM Clasificar → Construir Respuesta → Respond to Webhook`
- **Si no hay casos parecidos:** `→ Responder Sin Casos → Respond to Webhook`

**Nodos clave:**

| Nodo | Qué hace |
|---|---|
| `Preparar ticket` | Valida que el texto tenga entre 10 y 2000 caracteres |
| `Comprobar Coleccion` / `¿Existe coleccion?` | Preguntan a Qdrant si `tickets_historicos` ya existe |
| `Preparar Contexto RAG` | Descarta casos con similitud menor a 0.35 y arma el prompt (el ticket y los casos se tratan como datos, no como instrucciones) |
| `LLM Clasificar` | Temperatura 0.2 y respuesta solo en JSON |
| `Construir Respuesta` | Valida categoría, prioridad y área contra las listas permitidas, limita la confianza entre 0 y 1 y descarta ids de casos que no se le dieron al modelo |

**Cosas que se pueden ajustar:**

- Si n8n corre **fuera de Docker**, cambia `http://host.docker.internal:6333` por `http://localhost:6333` en los nodos `Preparar Tickets` y `Preparar ticket`.
- Para cambiar el umbral de similitud, edita la constante `UMBRAL` en `Preparar Contexto RAG`.
- Para agregar o cambiar tickets históricos, edita el arreglo en el nodo `Preparar Tickets` y borra la colección:
  ```bash
  curl -X DELETE http://localhost:6333/collections/tickets_historicos
  ```
  El siguiente ticket que llegue la vuelve a crear con los datos nuevos.

---

## 📁 Estructura del proyecto

```
Analizador-de-tickets-de-soporte/
├── Workflow/
│   └── workflow_n8n_tickets.json    workflow para importar en n8n
└── analizador-tickets/
    ├── build.gradle                 dependencias de Gradle
    ├── settings.gradle
    ├── docker-compose.yml           n8n + ngrok + Qdrant + PostgreSQL
    ├── .env                         (lo creas tú, no se sube a GitHub)
    └── src/main/
        ├── java/com/ejemplo/tickets/
        │   ├── TicketsApplication.java   punto de entrada
        │   ├── config/        AppProperties, ClientesConfig (cliente HTTP de n8n), CorsConfig
        │   ├── controller/    AnalisisController (/analizar, /historial), SaludController (/salud)
        │   ├── service/       AnalisisService (flujo completo), N8nService (llama a n8n), Catalogo (valores permitidos)
        │   ├── model/         AnalisisTicket (entidad del historial)
        │   ├── repository/    AnalisisTicketRepository
        │   ├── dto/           SolicitudAnalisis, RespuestaAnalisis, ResultadoLlm, CasoSimilar
        │   └── exception/     ManejadorErrores, ServicioExternoException
        └── resources/
            ├── application.properties
            └── static/        frontend (index.html, styles.css, app.js)
```

---

## 🧪 Casos de prueba

1. *"Me cobraron dos veces el pedido 4521..."* → Facturación, alta, Finanzas.
2. *"No puedo entrar a mi cuenta..."* → Cuenta y acceso, media, TI.
3. *"Veo compras en mi tarjeta que yo no hice..."* → Seguridad, alta, Seguridad.
4. Ticket mixto (cobro duplicado y producto que no llegó) → cita casos de dos categorías y baja la confianza.
5. *"¿Cuál es la capital de Francia?"* → sin predicción, aviso de información insuficiente.
6. Texto muy corto → el frontend avisa y la API responde 400.

---

## 🛠️ Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `n8n respondió 404` | El workflow no está activo o usas `/webhook-test/`. Actívalo y usa `/webhook/analizar-ticket`. |
| `No se pudo conectar con n8n` | n8n apagado o URL incorrecta. Haz `docker compose up -d` y revisa `N8N_WEBHOOK_URL` en el `.env`. |
| `n8n respondió con error 500` | Abre *Executions* en n8n y mira el nodo en rojo: normalmente es la credencial de OpenRouter sin asignar o Qdrant sin responder. |
| Nodo `Embedding` / `LLM` con error 401 | Credencial sin asignar, o clave incorrecta/revocada. |
| `Comprobar Coleccion` o `Buscar en Qdrant` fallan | Qdrant apagado o no alcanzable desde n8n (revisa `host.docker.internal`). |
| `docker compose` dice que falta `NGROK_AUTHTOKEN` | Agrégalo al `.env`. |
| Muchos tickets caen en "información insuficiente" | Baja `UMBRAL` a 0.25 en `Preparar Contexto RAG` o agrega más tickets históricos. |
| Error de base de datos | Revisa que los datos de `application.properties` (`tickets` / `tickets` / puerto `5433`) coincidan con los de Docker. Si cambiaste `DB_PASSWORD`, cámbiala también en `application.properties` y, si ya se había creado el volumen, haz `docker compose down -v`. |
| El frontend no conecta con el backend | Abre la página desde http://localhost:8081. Si usas Live Server (puerto 5500), el CORS ya está permitido. |

---

## 🌱 Mejoras futuras

- Login con JWT y tickets por usuario
- Carga de tickets desde CSV en lote
- Botón de retroalimentación para añadir tickets corregidos a Qdrant
- Panel de estadísticas por categoría y área
- Mover usuario y contraseña de la base de datos al `.env`

---

## Teabajo realizado por:
Lisseth Zapata
