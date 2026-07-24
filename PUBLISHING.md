# Publicar en Maven Central

Guía para publicar `spring-fluent-query-core` y `spring-fluent-query-spring-boot-starter` en [Maven Central](https://central.sonatype.com/).

Opciones de deploy: **Docker** (`.env` local) o **GitHub Actions** (tag `v*` → draft Release → Publish release → Maven; `autoPublish=true` en Central Portal; ver [.github/workflows/release.yml](.github/workflows/release.yml)).

## Qué se publica

| Artefacto | ¿Se publica? |
|-----------|--------------|
| `spring-fluent-query-core` | Sí |
| `spring-fluent-query-spring-boot-starter` | Sí |
| `spring-fluent-query` (POM padre) | Sí |
| `spring-fluent-query-example` | **No** (excluido con `-pl core,starter -am` en el deploy) |

## Requisitos (una sola vez)

### 1. Repositorio en GitHub

Repo público: `https://github.com/BenjaminOR-dev/spring-fluent-query`

Namespace Maven verificado: **`io.github.benjaminor-dev`** (Central Portal → pestaña Namespace).

### 2. Namespace en Central Portal

Ya verificado vía GitHub como **`io.github.benjaminor-dev`**. El `groupId` del proyecto debe coincidir.

### 3. Clave GPG

```bash
gpg --full-generate-key
gpg --list-secret-keys --keyid-format long
gpg --keyserver keys.openpgp.org --send-keys TU_KEY_ID
```

### 4. Credenciales locales (deploy con Docker)

Copia [.env.example](.env.example) a `.env` (gitignored) y rellena:

| Variable | Origen |
|----------|--------|
| `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` | [Central Portal → User Token](https://central.sonatype.com/usertoken) |
| `GPG_KEY_ID` | `gpg --list-secret-keys --keyid-format long` |
| `GPG_PASSPHRASE` | Passphrase de tu clave GPG |

Backup de clave privada GPG: `.local/gpg-signing-private.asc` (gitignored vía `.local/`).

Para flujos sin Docker, puedes usar [docs/settings-central.xml.example](docs/settings-central.xml.example) en `~/.m2/settings.xml`.

> **Seguridad — nunca commitees:**
>
> - `.env` (credenciales reales)
> - `.local/` (material de claves GPG)
> - Tokens de Central Portal o passphrases GPG en el repositorio

## Checklist de release

### 1. Versión de release (sin SNAPSHOT)

**Todo en un solo commit**, antes de crear el tag. Si falta alguno, el tag quedará desalineado.

#### POMs (4 archivos)

| Archivo | Qué cambiar |
|---------|-------------|
| `pom.xml` (raíz) | `<version>` y `<scm><tag>vX.Y.Z</tag></scm>` |
| `spring-fluent-query-core/pom.xml` | `<version>` del parent |
| `spring-fluent-query-spring-boot-starter/pom.xml` | `<version>` del parent |
| `spring-fluent-query-example/pom.xml` | `<version>` del parent |

```xml
<version>0.2.0</version>
```

```xml
<scm>
    ...
    <tag>v0.2.0</tag>
</scm>
```

#### README (3 archivos — sección **Inicio rápido → Dependencias**)

En cada uno actualiza **Maven**, **Gradle Kotlin** y **Gradle Groovy**:

| Archivo | Líneas típicas |
|---------|----------------|
| `README.md` | `<version>…</version>` + `implementation(...)` × 2 |
| `README.es.md` | igual |
| `README.pt.md` | igual |

Ejemplo:

```xml
<version>0.2.0</version>
```

```kotlin
implementation("io.github.benjaminor-dev:spring-fluent-query-spring-boot-starter:0.2.0")
```

#### Opcional (recomendado)

| Dónde | Qué |
|-------|-----|
| `PUBLISHING.md` → *Dependencia para consumidores* | Ejemplos Maven/Gradle con la nueva versión |

#### Verificación rápida

```bash
grep -r "SNAPSHOT" pom.xml */pom.xml   # no debe quedar SNAPSHOT de release
grep "spring-fluent-query-spring-boot-starter:0\." README*.md  # misma versión en los 3
```

Maven Central no acepta `-SNAPSHOT` en el repositorio de releases.

### 2. Ejecutar tests

```bash
docker compose run --rm maven mvn clean verify
docker compose run --rm maven mvn clean verify -Pboot4
```

### 3. Deploy con perfil `release`

Asegúrate de tener `.env` (desde `.env.example`) y la clave GPG en `.local/`.

```bash
docker compose run --rm maven ./docker/deploy-release.sh
```

El script lee `.env`, genera `settings.xml` en el contenedor, importa la clave desde `.local/` y ejecuta:

```bash
mvn clean deploy -Prelease -pl spring-fluent-query-core,spring-fluent-query-spring-boot-starter -am
```

Los flags `-pl … -am` publican solo **core**, **starter** y el POM padre. El módulo **example** se compila en local pero **no** se sube a Maven Central.

Notas:

- No montes `~/.gnupg` de macOS — `gpg-agent` falla en Docker.
- El perfil `release` usa GPG en modo loopback (sin agent).
- La caché Maven usa el volumen `.m2/` del proyecto en `docker-compose.yml`.

Alternativa: ejecutar el deploy **en el host** (fuera de Docker) si tienes Maven y GPG locales:

```bash
export GPG_TTY=$(tty)
mvn clean deploy -Prelease \
  -pl spring-fluent-query-core,spring-fluent-query-spring-boot-starter \
  -am
```

### 4. Release con GitHub Actions

Flujo en dos pasos ([.github/workflows/release.yml](.github/workflows/release.yml)):

```text
git push origin vX.Y.Z  →  Action crea un GitHub Release en draft
tú: Publish release     →  Action hace deploy a Maven Central
Central Portal          →  publicación automática (autoPublish=true)
```

1. Deja el POM en versión **release** (sin `-SNAPSHOT`), commit y tag.
2. `git push origin vX.Y.Z` → aparece un **draft** en GitHub → Releases.
3. Revisas notas / commit y pulsas **Publish release**.
4. Eso dispara el deploy de **core**, **starter** y el POM padre (igual que `deploy-release.sh`).

Configura estos [secrets del repositorio](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions):

| Secret | Valor |
|--------|-------|
| `CENTRAL_USERNAME` | Username del user token de Central Portal |
| `CENTRAL_PASSWORD` | Password del user token de Central Portal |
| `GPG_KEY_ID` | Id de clave GPG (formato long) |
| `GPG_PASSPHRASE` | Passphrase de la clave GPG |
| `GPG_PRIVATE_KEY` | Clave privada armored (contenido de `.local/gpg-signing-private.asc`) |

### 5. Publicar en Central Portal

Con `autoPublish=true` (config actual), tras el upload del paso 4 Central Portal publica automáticamente.

No hace falta pulsar **Publish** en Sonatype. Si un deployment queda en validación fallida, revisa [central.sonatype.com](https://central.sonatype.com/).

### 6. Tag en Git

El tag debe apuntar al **commit del paso 1** (POMs + README + `<scm><tag>` ya incluidos):

```bash
git tag -a v0.2.0 -m "Release 0.2.0"
git push origin v0.2.0
```

Eso **solo** crea el draft en GitHub; el deploy a Maven ocurre cuando publiques el Release.

### 7. Subir versión de desarrollo

En los **4 POMs** (mismo listado del paso 1), commit separado en `main`:

```xml
<version>0.2.0-SNAPSHOT</version>
```

Los README **no** cambian aquí — siguen mostrando la última versión publicada en Central.

## Dependencia para consumidores (tras publicar)

**Maven**

```xml
<dependency>
    <groupId>io.github.benjaminor-dev</groupId>
    <artifactId>spring-fluent-query-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
```

**Gradle**

```kotlin
implementation("io.github.benjaminor-dev:spring-fluent-query-spring-boot-starter:0.2.0")
```

Sin repositorios extra. Añade también `spring-boot-starter-data-jpa` en tu app (el starter lo marca como opcional).

## Problemas frecuentes

| Problema | Solución |
|----------|----------|
| `401 Unauthorized` en deploy | Revisa token en `~/.m2/settings.xml`, server id = `central` |
| Falla firma GPG | Verifica `gpg.keyname`, monta clave vía `.local/`, o `export GPG_TTY=$(tty)` |
| Namespace no permitido | Confirma `groupId` = `io.github.benjaminor-dev` y namespace **Verified** en Central Portal |
| Errores de Javadoc | `failOnError=false` en el perfil release; mejora docs después |
| Se subió el example | El deploy usa `-pl core,starter -am`; example queda excluido de Central |

