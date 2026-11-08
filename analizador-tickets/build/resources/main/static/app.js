/* ============================================================
   Frontend del analizador de tickets.
   Habla con la API de Spring Boot: POST /analizar y GET /historial.
   ============================================================ */

// Si la pagina se sirve desde Spring Boot (puerto 8081) usamos rutas relativas
// Si se abre desde otro lugar (por ejemplo Live Server en 5500) apuntamos al backend
// (location.port es el puerto de la pagina actual; "" + "/analizar" = "/analizar")
const API = location.port === "8081" ? "" : "http://localhost:8081";

// Guardo los elementos del HTML que voy a usar mas de una vez (los busco por su id)
const texto = document.getElementById("texto");
const btnAnalizar = document.getElementById("btnAnalizar");
const cargando = document.getElementById("cargando");
const resultado = document.getElementById("resultado");
const historial = document.getElementById("historial");

/* ---------- Utilidades ---------- */

// Evita inyectar HTML: el texto del ticket y la respuesta del LLM no son confiables
// Cambia los caracteres peligrosos (< > & ") por versiones seguras para que
// si alguien escribe <script>... se vea como texto y no se ejecute (seguridad XSS)
function escapar(valor = "") {
    return String(valor)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;")
        .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

// Muestra el tiempo bonito: menos de 24 h se deja en horas; si es mas, agrega los dias
// Ej: 48 -> "48 horas (≈ 2 días)"
function formatearTiempo(horas) {
    if (horas < 24) return `${horas} horas`;
    // toFixed(1) deja 1 decimal y replace(".0","") quita el ".0" inutil (2.0 -> 2)
    const dias = (horas / 24).toFixed(1).replace(".0", "");
    return `${horas} horas (≈ ${dias} día${dias === "1" ? "" : "s"})`;
}

// Crea la etiqueta de color segun la prioridad (alta = rojo, media = amarillo, baja = verde).
// Las clases estan definidas en styles.css
function badgePrioridad(prioridad) {
    // Solo uso la clase si es un valor conocido; asi nunca meto algo raro en el HTML
    const clase = ["alta", "media", "baja"].includes(prioridad) ? prioridad : "";
    return `<span class="badge ${clase}">${escapar(prioridad)}</span>`;
}

/* ---------- Analizar ---------- */

// Funcion principal: manda el ticket a la API y muestra lo que responde
// "async" porque usa await para esperar la respuesta del servidor sin congelar la pagina
async function analizar() {
    // trim() quita los espacios del inicio y final
    const ticket = texto.value.trim();

    // Validacion rapida en el navegador (el backend igual lo vuelve a validar)
    if (ticket.length < 10) {
        alert("Escribe el ticket con al menos 10 caracteres.");
        return;
    }

    // Estado "cargando": muestro el mensaje, limpio el resultado anterior
    // y deshabilito el boton para que no hagan clic dos veces
    cargando.classList.remove("oculto");
    resultado.innerHTML = "";
    btnAnalizar.disabled = true;

    try {
        // Llamo al backend: POST /analizar con el cuerpo {"texto": "..."}
        const respuesta = await fetch(`${API}/analizar`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ texto: ticket })
        });

        // Leo el JSON; si no se puede leer, uso un objeto vacio para que no reviente
        const datos = await respuesta.json().catch(() => ({}));
        // Si el servidor respondio con error (400, 502...), lanzo un error con el mensaje que mando
        // (el backend siempre devuelve { "error": "..." }, gracias a ManejadorErrores)
        if (!respuesta.ok) throw new Error(datos.error || "Error del servidor.");

        mostrarResultado(datos);
        // Recargo el historial para que aparezca el analisis que acabo de hacer
        cargarHistorial();
    } catch (error) {
        console.error(error);
        // fetch lanza TypeError cuando ni siquiera logra conectarse (backend apagado)
        const mensaje = error instanceof TypeError
            ? "No se pudo conectar con el servidor. Verifica que Spring Boot esté en ejecución."
            : error.message;
        resultado.innerHTML = `<div class="error">❌ ${escapar(mensaje)}</div>`;
    } finally {
        // finally corre SIEMPRE (haya error o no): vuelvo a dejar todo normal
        cargando.classList.add("oculto");
        btnAnalizar.disabled = false;
    }
}

/* ---------- Render del resultado ---------- */

// Arma la tarjeta principal con la prediccion (categoria, prioridad, area, tiempo, confianza)
function renderAnalisis(a) {
    // La confianza llega de 0 a 1; la paso a porcentaje (0.86 -> 86)
    // "??" usa 0 si viene null o undefined
    const confianza = Math.round((a.confianza ?? 0) * 100);
    // OJO: la barra de confianza (.progreso) se llena con ese mismo porcentaje usando style="width:...%"
    return `
        <div class="tarjeta-analisis">
            <p class="etiqueta">✦ Predicción basada en casos históricos similares</p>
            <div class="datos">
                <div class="dato"><small>Categoría</small><strong>${escapar(a.categoria)}</strong></div>
                <div class="dato"><small>Prioridad</small>${badgePrioridad(a.prioridad)}</div>
                <div class="dato"><small>Área sugerida</small><strong>${escapar(a.area_sugerida)}</strong></div>
                <div class="dato"><small>Tiempo estimado</small><strong>${escapar(formatearTiempo(a.tiempo_estimado_horas))}</strong></div>
                <div class="dato">
                    <small>Confianza</small><strong>${confianza}%</strong>
                    <div class="barra"><div class="progreso" style="width:${confianza}%"></div></div>
                </div>
            </div>
            <p class="justificacion">${escapar(a.justificacion)}</p>
        </div>`;
}

// Arma la tarjeta de UN ticket historico parecido
// idsCitados = ids que el modelo uso; a esos les pongo la etiqueta "usado por el modelo"
function renderSimilar(caso, idsCitados) {
    const citado = idsCitados.includes(caso.id);
    return `
        <div class="similar ${citado ? "citado" : ""}">
            <div class="cabecera">
                <span class="texto">${escapar(caso.texto)}${citado ? '<span class="cita">usado por el modelo</span>' : ""}</span>
                <span class="porcentaje">${caso.similitud_porcentaje}%</span>
            </div>
            <div class="meta">
                caso:${caso.id} · ${escapar(caso.categoria)} · ${escapar(caso.area)} ·
                resuelto en ${caso.horas_resolucion} h
            </div>
            ${badgePrioridad(caso.prioridad)}
        </div>`;
}

// Junta todo y lo pinta en pantalla
function mostrarResultado(datos) {
    // "?." y "??" evitan errores si algun dato viene null
    const similares = datos.similares ?? [];
    const idsCitados = datos.analisis?.casos_usados ?? [];

    // Si hay analisis muestro la tarjeta; si no (ticket sin relacion) muestro el aviso amarillo
    const principal = datos.analisis
        ? renderAnalisis(datos.analisis)
        : `<div class="aviso">⚠️ ${escapar(datos.mensaje || "No se pudo generar una predicción.")}</div>`;

    // Lista de similares (solo si hay alguno): map() crea una tarjeta por caso y join() las une
    const bloqueSimilares = similares.length
        ? `<div class="subtitulo">
               <h2>Tickets similares que respaldan la predicción</h2>
               <span>${similares.length} coincidencias</span>
           </div>
           ${similares.map(c => renderSimilar(c, idsCitados)).join("")}`
        : "";

    resultado.innerHTML = principal + bloqueSimilares;
}

/* ---------- Historial ---------- */

// Trae los ultimos 20 analisis (GET /historial) y los lista abajo
async function cargarHistorial() {
    try {
        const respuesta = await fetch(`${API}/historial`);
        // Si falla, salto directo al catch
        if (!respuesta.ok) throw new Error();
        const filas = await respuesta.json();

        // Ojo: aca los campos vienen en camelCase (creadoEn, areaSugerida) porque es la entidad
        // de la BD y no el DTO del LLM, que usa snake_case
        historial.innerHTML = filas.length
            ? filas.map(f => `
                <div class="fila-historial">
                    <div class="meta">
                        ${new Date(f.creadoEn).toLocaleString("es")} ·
                        ${escapar(f.categoria)} · ${escapar(f.areaSugerida)} ·
                        ${escapar(f.tiempoEstimadoHoras)} h
                    </div>
                    ${badgePrioridad(f.prioridad)}
                    <div>${escapar(f.textoTicket)}</div>
                </div>`).join("")
            : '<p class="vacio">Todavía no has analizado ningún ticket.</p>';
    } catch {
        historial.innerHTML = '<p class="vacio">No se pudo cargar el historial.</p>';
    }
}

/* ---------- Eventos ---------- */

// Clic en "Analizar ticket"
btnAnalizar.addEventListener("click", analizar);

// Ctrl + Enter dentro del textarea también analiza (metaKey = Cmd en Mac)
texto.addEventListener("keydown", e => {
    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) analizar();
});

// Botones de ejemplo: ponen su texto (data-texto) en el textarea y analizan al toque
document.querySelectorAll(".ejemplo").forEach(boton => {
    boton.addEventListener("click", () => {
        texto.value = boton.dataset.texto;
        analizar();
    });
});

// Apenas abre la pagina, cargo el historial
cargarHistorial();
