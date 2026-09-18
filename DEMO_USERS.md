# Usuarios de demostración

Estas cuentas son exclusivamente para ambientes locales, académicos o de demostración. No deben habilitarse en una base de datos de producción con información real.

| Perfil | Correo | Teléfono | Contraseña | Estado | Rol |
|---|---|---|---|---|---|
| Ana Organizadora | `ana.organizadora@eventflow.demo` | `+502 5555 0101` | `EventFlowDemo1!` | Activa | Organizer |
| Carlos Organizador | `carlos.organizador@eventflow.demo` | `+502 5555 0102` | `EventFlowDemo2!` | Activa | Organizer |

Las dos cuentas permiten crear eventos independientes y verificar que un organizador no pueda consultar ni modificar los eventos del otro.

## Creación local

Configura la variable antes de iniciar la API en ambiente Development:

```bash
export SeedDemoData__Enabled=true
dotnet run --project src/Api
```

## Creación mediante Cloud Run Job

Agrega esta variable únicamente al Job de migraciones:

```text
SeedDemoData__Enabled=true
```

El proceso es idempotente: identifica las cuentas por correo normalizado y no las duplica al ejecutar nuevamente el Job.
