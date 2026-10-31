# Corrección de los 14 fallos de integración

## Resultados comprobados

| Ejecución | Resultado |
|---|---|
| Nueva copia PostgreSQL 18.6, restaurada desde public verificado y migrada con Flyway V1–V21 | 36 pruebas aprobadas, 0 fallos, 0 errores, 0 omitidas |
| Hibernate validate sobre esa copia | Correcto |
| JDK 25: `gradlew.bat clean build --console=plain` | BUILD SUCCESSFUL; 36 aprobadas, 0 fallos/errores/omitidas |

La primera ejecución usó PostgreSQL real, driver PostgreSQL, ddl-auto=validate y
Flyway desactivado durante los tests (esquema ya migrado). No usó H2. El build normal
se ejecutó después con la configuración habitual H2 de src/test/resources; no se
usó como sustituto de la comprobación PostgreSQL.

Evidencias PostgreSQL conservadas antes de clean:
`backups/migration-rehearsal-1793402673082/postgresql-test-results/`,
`postgresql-test-summary.html`, `postgresql-test-summary.csv` y `result.txt`.
El HTML habitual en build/reports/tests/test corresponde al build normal posterior.

## Causas raíz y cambios

### Dependencias de módulos: 13 pruebas

Los XML mostraban ConflictException en EventService.configureModule por dependencias
persistidas por V5: MAP→EVT, GAM→INT, BKG→CAL, QUE→GST, NOT→EVT, RSC→EVT, ORD→GST,
REV→RES, NET→GST y GST→INV. El esquema H2 habitual tenía las tablas, pero no esas
semillas Flyway, y ocultaba la preparación incompleta de los fixtures.

ModuleTestFixtures consulta el grafo REAL de ModuleDependencyRepository y habilita
sus prerrequisitos en orden mediante EventService. Detecta ciclos y no cambia
dependencias ni validaciones. Prerrequisitos exclusivos de preparación se configuran
guestVisible=false; los módulos explícitos del escenario conservan su configuración
y navegación esperadas. Ninguna aserción existente se debilitó ni se modificó para
aceptar un resultado incorrecto.

El registro de catálogo ahora agrega únicamente códigos ausentes; no sobrescribe
metadatos del catálogo migrado compartido. Se centralizó la preparación también en
los demás fixtures de módulos para evitar variantes inconsistentes.

### Marketplace: lower(bytea) con filtros nulos

El JPQL combinaba `:category is null` con `lower(:category)` y hacía algo similar con
location. PostgreSQL recibía un binding nulo sin tipo textual compatible y rechazaba
lower(bytea). Se reemplazó por Specification/Criteria con expresiones tipadas y
predicados solo para filtros presentes. No se enlazan nulos dentro de lower().

Se conservan tipo/status ACTIVE, categoría sin distinguir mayúsculas, coincidencia
parcial de ubicación, capacidad mínima, precio máximo, puntuación mínima,
paginación y orden descendente por createdAt/id. El puerto de aplicación y la API
HTTP no cambiaron. El caso de prueba existente ahora cubre adicionalmente categoría
sola, ubicación sola y ambos filtros de texto ausentes, sin aumentar ni omitir tests.

## Aislamiento y limpieza

El ejecutor local no lee .env ni construye conexiones remotas. Cada ejecución crea
un clúster/base nuevos desde el respaldo y una segunda base clonada para los tests.
No reutiliza datos de ejecuciones anteriores ni borra bases existentes. Las filas
de negocio de los casos usan propietarios/eventos propios; el catálogo compartido
ya no se sobrescribe. Las búsquedas globales del marketplace quedan cubiertas con
datos migrados presentes, no una base vacía que esconda el problema.

No se envolvió toda la suite en una transacción ni se añadió TRUNCATE: las pruebas
de conflictos siguen ejercitando transacciones y restricciones reales. Los datos
cometidos de prueba quedan confinados al clúster desechable, detenido al finalizar;
no se altera la copia maestra migrada ni el respaldo. No se limpiaron filas del origen.

## Archivos de código modificados en esta tarea

Producción:

- src/main/kotlin/com/eventflow/eventflow_api/marketplace/infrastructure/persistence/MarketplaceOfferingRepository.kt

Nuevo soporte de pruebas:

- src/test/kotlin/com/eventflow/eventflow_api/support/ModuleTestFixtures.kt

Fixtures/casos existentes actualizados:

- src/test/kotlin/com/eventflow/eventflow_api/EventflowApiApplicationTests.kt
- src/test/kotlin/com/eventflow/eventflow_api/gamification/GamificationServiceIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/interaction/DrawServiceIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/marketplace/MarketplaceRulesIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/module/ExtensionRulesIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/module/ModuleAssociationsIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/module/ModuleVisibilityIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/module/OrderRulesIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/module/PollRulesIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/module/SurveyRulesIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/networking/MeetingServiceIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/resource/CertificateServiceIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/sport/MatchResultServiceIntegrationTest.kt
- src/test/kotlin/com/eventflow/eventflow_api/ticketing/TicketingFlowTest.kt

Esta documentación es adicional. Se conservaron los cambios previos que ya había
en el workspace. No se modificaron migraciones ni configuración/secretos en .env.

No quedan fallos en las dos suites ejecutadas. Los riesgos previamente documentados
de Supabase (permisos, transición de usuarios y semillas demo) siguen siendo decisiones
separadas antes de autorizar cambios remotos. No hubo conexiones ni cambios en Supabase,
migraciones remotas, repair/baseline, commits ni push.
