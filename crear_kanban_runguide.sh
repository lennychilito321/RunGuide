#!/usr/bin/env bash
# ============================================================================
# RunGuide — crea el tablero Scrum en GitHub Projects con las 20 HU
# Requiere: gh (GitHub CLI) y jq. Funciona en bash 3.2+ (Mac, Linux, Git Bash).
#
# Uso:
#   1) Edita la sección CONFIGURACIÓN (repo, dueño, usuarios de GitHub, roles).
#   2) gh auth login
#      gh auth refresh -s project        # permiso para GitHub Projects
#   3) bash crear_kanban_runguide.sh
#
# Ejecútalo UNA sola vez: si lo repites duplicará los issues.
# ============================================================================
set -euo pipefail

# ============================ CONFIGURACIÓN ================================
REPO="USUARIO_O_ORG/RunGuide"       # repositorio donde se crean los issues
OWNER="USUARIO_O_ORG"               # dueño del proyecto (usuario u organización)
PROJECT_NUMBER=""                   # vacío = crear un proyecto nuevo
PROJECT_TITLE="RunGuide — Tablero Scrum"

# Usuarios de GitHub de cada integrante (obligatorio cambiarlos)
GH_LENNY="usuario-lenny"
GH_JUAN="usuario-juan"
GH_ANGELA="usuario-angela"
GH_LEONARD="usuario-leonard"
GH_DAVID="usuario-david"

# Roles Scrum. El documento dice «Por confirmar»: esto es una PROPUESTA
# basada en la carga de trabajo. Cámbiala aquí y el script lo respeta.
#   Valores válidos: SM | PO | UI | DATOS | DEV
ROL_LENNY="SM"        # Scrum Master y desarrollador (pruebas, revisión, sesión)
ROL_JUAN="UI"         # Desarrollador — interfaz (catálogo, detalle, mapa)
ROL_ANGELA="DATOS"    # Desarrollador — datos (Firebase, panel de admin)
ROL_LEONARD="PO"      # Product Owner (valida HU; además desarrolla GPS)
ROL_DAVID="DEV"       # Desarrollador (favoritos y opiniones)

SPRINT_ACTUAL="Sprint 1"            # 30 oct – 5 nov 2026
# ===========================================================================

command -v gh >/dev/null || { echo "Falta instalar gh (https://cli.github.com)"; exit 1; }
command -v jq >/dev/null || { echo "Falta instalar jq"; exit 1; }

# ---------- utilidades de roles ----------
gh_user() { case "$1" in
  lenny) echo "$GH_LENNY";; juan) echo "$GH_JUAN";; angela) echo "$GH_ANGELA";;
  leonard) echo "$GH_LEONARD";; david) echo "$GH_DAVID";; *) echo "";; esac; }

codigo_rol() { case "$1" in
  lenny) echo "$ROL_LENNY";; juan) echo "$ROL_JUAN";; angela) echo "$ROL_ANGELA";;
  leonard) echo "$ROL_LEONARD";; david) echo "$ROL_DAVID";; esac; }

nombre_rol() { case "$1" in
  SM) echo "Scrum Master y desarrollador";; PO) echo "Product Owner";;
  UI) echo "Desarrollador — interfaz";; DATOS) echo "Desarrollador — datos";;
  DEV) echo "Desarrollador";; esac; }

etiqueta_rol() { case "$1" in
  SM) echo "rol: scrum-master";; PO) echo "rol: product-owner";;
  UI) echo "rol: dev-interfaz";; DATOS) echo "rol: dev-datos";; DEV) echo "rol: desarrollador";; esac; }

nombre_persona() { case "$1" in
  lenny) echo "Lenny Alexander Chilito";; juan) echo "Juan Nicolás Vargas Rojas";;
  angela) echo "Ángela Bolañoz Gaviria";; leonard) echo "Leonard Rincón";;
  david) echo "David Daniel López Guzmán";; esac; }

# Revisor sugerido según el rol: interfaz <-> datos se revisan entre sí;
# el resto se cruza. (Regla del documento: nadie sube a main sin revisión.)
revisor_de() { case "$1" in
  juan) echo angela;; angela) echo juan;; david) echo lenny;;
  lenny) echo david;; leonard) echo lenny;; esac; }

# ---------- 1. Proyecto ----------
if [ -z "$PROJECT_NUMBER" ]; then
  echo "Creando proyecto…"
  PJ=$(gh project create --owner "$OWNER" --title "$PROJECT_TITLE" --format json)
  PROJECT_NUMBER=$(jq -r .number <<<"$PJ")
fi
PID=$(gh project view "$PROJECT_NUMBER" --owner "$OWNER" --format json | jq -r .id)
gh project link "$PROJECT_NUMBER" --owner "$OWNER" --repo "$REPO" >/dev/null 2>&1 || true

# ---------- 2. Campos del tablero ----------
echo "Creando campos…"
mkfield() { gh project field-create "$PROJECT_NUMBER" --owner "$OWNER" --name "$1" \
  --data-type SINGLE_SELECT --single-select-options "$2" >/dev/null 2>&1 || true; }
mkfield "Etapa"        "Por hacer,En progreso,En revisión,Hecho"
mkfield "Prioridad"    "Alta,Media,Baja"
mkfield "Sprint"       "Sin asignar,Sprint 1,Sprint 2,Sprint 3"
mkfield "Módulo"       "Catálogo,Cuentas,Actividad GPS,Progreso,Favoritos,Opiniones,Administración"
mkfield "Rol del equipo" "Scrum Master y desarrollador,Product Owner,Desarrollador — interfaz,Desarrollador — datos,Desarrollador"

FIELDS=$(gh project field-list "$PROJECT_NUMBER" --owner "$OWNER" --format json)
fid() { jq -r --arg n "$1" '.fields[]|select(.name==$n)|.id' <<<"$FIELDS"; }
oid() { jq -r --arg n "$1" --arg o "$2" '.fields[]|select(.name==$n)|.options[]?|select(.name==$o)|.id' <<<"$FIELDS"; }
setf() { # item campo opción
  local f o; f=$(fid "$2"); o=$(oid "$2" "$3")
  [ -n "$f" ] && [ -n "$o" ] && gh project item-edit --id "$1" --project-id "$PID" \
    --field-id "$f" --single-select-option-id "$o" >/dev/null || echo "  (aviso: no se pudo fijar $2 = $3)"; }

# ---------- 3. Etiquetas (solo grises) ----------
echo "Creando etiquetas…"
lbl() { gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force >/dev/null; }
lbl "HU"                  "404040" "Historia de usuario"
lbl "prioridad: alta"     "262626" "Must have"
lbl "prioridad: media"    "7F7F7F" "Should have"
lbl "prioridad: baja"     "BFBFBF" "Could have"
lbl "sprint-1"            "595959" "Sprint 1 (30 oct – 5 nov 2026)"
for r in SM PO UI DATOS DEV; do lbl "$(etiqueta_rol $r)" "A6A6A6" "$(nombre_rol $r)"; done

slug_modulo() { case "$1" in
  "Catálogo") echo catalogo;; "Cuentas") echo cuentas;; "Actividad GPS") echo actividad-gps;;
  "Progreso") echo progreso;; "Favoritos") echo favoritos;; "Opiniones") echo opiniones;;
  "Administración") echo administracion;; *) echo historia;; esac; }

# ---------- 4. Texto común de cada issue ----------
DOD='**Definición de Hecho**
- [ ] Todos los criterios de aceptación se verificaron y se cumplen
- [ ] El código compila sin errores y la app no se cierra al usarla
- [ ] Revisado por otro integrante e integrado en `main`
- [ ] Casos de error con mensajes claros (invitado, sin conexión, permiso denegado)
- [ ] El Product Owner la validó en la Sprint Review'

extra_sprint1() { case "$1" in
HU-12) cat <<'X'
**Qué se hace:** ícono de corazón que se rellena al tocarlo; mensaje «Añadido a favoritos»; sin duplicados; diálogo «Inicia sesión para guardar» (Cancelar / Iniciar sesión) para el invitado; el estado del corazón se conserva al reabrir el detalle y en otro dispositivo. Mejora el botón de texto actual.

**Tareas:** T4 (ícono, mensajes, diálogo de invitado, persistencia sin duplicados) · T6 (pruebas con sesión, invitado y sin conexión).
**Riesgo:** depende de que el inicio de sesión funcione (ya existe en el proyecto base).
X
;; HU-13) cat <<'X'
**Qué se hace:** nueva pantalla «Favoritos» con la misma tarjeta del catálogo; retirar con el corazón y mensaje «Sendero retirado de favoritos» con «Deshacer» (5 s); estado vacío «Aún no tienes favoritos» con botón «Explorar senderos».

**Tareas:** T5 (pantalla, estado vacío, retirar y deshacer) · T6 (pruebas).
X
;; HU-02) cat <<'X'
**Ampliación — mapa de la ruta (Google Maps).**
**Criterios nuevos (el PO debe confirmarlos):**
1. El detalle muestra un mapa con la ruta trazada y marcadores de inicio y fin.
2. El mapa se centra y ajusta el zoom para que toda la ruta sea visible.
3. Sin coordenadas: se muestra «Ruta no disponible» sin cerrar la app.
4. Si el mapa no carga (sin conexión o clave inválida): mensaje claro y el resto del detalle sigue funcionando.

**Tareas:** T1 (SDK y clave de API, **la clave no se sube al repositorio**) · T2 (estructura de ruta en Firebase y datos de prueba) · T3 (mapa y trazado en el detalle) · T6 (pruebas).
**Riesgo:** sin la clave (T1) o sin coordenadas (T2) el mapa no carga.
X
;; *) echo "El detalle y los criterios de aceptación están en el formato de historias de usuario (20 HU).";; esac; }

# ---------- 5. Historias ----------
# código | historia | rol de usuario | prioridad | módulo | responsable | apoyo | sprint | etapa
HU_DATA='HU-01|Explorar el catálogo de senderos|Invitado / Usuario|Alta|Catálogo|juan||Sin asignar|Por hacer
HU-02|Consultar el detalle de un sendero (mapa de la ruta)|Invitado / Usuario|Alta|Catálogo|juan|leonard|Sprint 1|En progreso
HU-03|Filtrar senderos por dificultad|Invitado / Usuario|Alta|Catálogo|juan||Sin asignar|Por hacer
HU-04|Registrarse en la aplicación|Invitado|Alta|Cuentas|lenny||Sin asignar|Por hacer
HU-05|Iniciar sesión|Usuario / Admin|Alta|Cuentas|lenny||Sin asignar|Por hacer
HU-06|Cerrar sesión|Usuario / Admin|Alta|Cuentas|lenny||Sin asignar|Por hacer
HU-07|Iniciar una actividad con seguimiento GPS|Usuario|Alta|Actividad GPS|leonard||Sin asignar|Por hacer
HU-08|Pausar y reanudar una actividad|Usuario|Alta|Actividad GPS|leonard||Sin asignar|Por hacer
HU-09|Finalizar y guardar una actividad|Usuario|Alta|Actividad GPS|leonard||Sin asignar|Por hacer
HU-10|Ver mis estadísticas básicas|Usuario|Alta|Progreso|lenny||Sin asignar|Por hacer
HU-11|Consultar mi historial de actividades|Usuario|Media|Progreso|lenny||Sin asignar|Por hacer
HU-12|Guardar un sendero en favoritos|Usuario|Alta|Favoritos|david||Sprint 1|En progreso
HU-13|Consultar y retirar mis favoritos|Usuario|Media|Favoritos|david|lenny|Sprint 1|En progreso
HU-14|Calificar un sendero|Usuario|Media|Opiniones|david||Sin asignar|Por hacer
HU-15|Escribir una reseña de un sendero|Usuario|Baja|Opiniones|david||Sin asignar|Por hacer
HU-16|Consultar las reseñas de un sendero|Invitado / Usuario|Media|Opiniones|david||Sin asignar|Por hacer
HU-17|Acceder al panel administrativo de senderos|Administrador|Alta|Administración|angela||Sin asignar|Por hacer
HU-18|Crear un sendero|Administrador|Alta|Administración|angela||Sin asignar|Por hacer
HU-19|Editar un sendero|Administrador|Media|Administración|juan||Sin asignar|Por hacer
HU-20|Eliminar un sendero|Administrador|Media|Administración|angela||Sin asignar|Por hacer'

echo "Creando las 20 historias…"
while IFS='|' read -r COD TIT ROLU PRI MOD RESP APOYO SPR ETAPA; do
  [ -z "$COD" ] && continue
  RC=$(codigo_rol "$RESP"); RN=$(nombre_rol "$RC")
  REV=$(revisor_de "$RESP")
  AS="$(gh_user "$RESP")"; [ -n "$APOYO" ] && AS="$AS,$(gh_user "$APOYO")"
  LABELS="HU,prioridad: $(echo "$PRI" | tr 'A-Z' 'a-z'),$(etiqueta_rol "$RC")"
  [ "$SPR" = "$SPRINT_ACTUAL" ] && LABELS="$LABELS,sprint-1"

  BODY="**Rol de usuario:** $ROLU · **Prioridad:** $PRI · **Módulo:** $MOD · **Sprint:** $SPR

**Responsable:** $(nombre_persona "$RESP") ($RN)"
  [ -n "$APOYO" ] && BODY="$BODY
**Apoyo:** $(nombre_persona "$APOYO") ($(nombre_rol "$(codigo_rol "$APOYO")"))"
  BODY="$BODY
**Revisor sugerido del PR:** $(nombre_persona "$REV") ($(nombre_rol "$(codigo_rol "$REV")"))
**Validación en la Sprint Review:** Product Owner ($(nombre_persona leonard))

$(extra_sprint1 "$COD")

**Rama sugerida:** \`feature/$COD-$(slug_modulo "$MOD")\`
Nadie sube directo a \`main\`.

$DOD"

  URL=$(gh issue create --repo "$REPO" --title "$COD: $TIT" --body "$BODY" \
        --label "$LABELS" --assignee "$AS")
  ITEM=$(gh project item-add "$PROJECT_NUMBER" --owner "$OWNER" --url "$URL" --format json | jq -r .id)

  setf "$ITEM" "Etapa"          "$ETAPA"
  setf "$ITEM" "Prioridad"      "$PRI"
  setf "$ITEM" "Sprint"         "$SPR"
  setf "$ITEM" "Módulo"         "$MOD"
  setf "$ITEM" "Rol del equipo" "$RN"
  echo "  ✔ $COD  → $(nombre_persona "$RESP") [$RN]  ($ETAPA)"
done <<<"$HU_DATA"

echo
echo "Listo. En el proyecto crea una vista Tablero agrupada por «Etapa»"
echo "(Por hacer → En progreso → En revisión → Hecho) y otra filtrada por «Sprint».";
echo "Tablero: https://github.com/orgs/$OWNER/projects/$PROJECT_NUMBER  (o /users/$OWNER/projects/$PROJECT_NUMBER)"
