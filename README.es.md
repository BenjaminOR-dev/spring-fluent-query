# Spring Fluent Query

[🇬🇧 English version](README.md) | [🇧🇷 Versão em português](README.pt.md)

[![Java](https://img.shields.io/badge/Java-17+-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-3.x%20%7C%204.x-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-data-jpa)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter?label=Maven%20Central)](https://search.maven.org/artifact/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Consultas fluidas **estilo Eloquent** sobre **Spring Data JPA Specifications**, sin Active Record.

Spring Fluent Query añade una cadena legible (`where` → `fetch` → `latest` / `first` / `page`) mientras la **ejecución** delega en la API fluida oficial de Spring Data `findBy(spec, q → …)`. Así, `first()` / `latest()` usan `LIMIT 1` **sin** un COUNT innecesario.

> Compatible con **Spring Boot 3.x y 4.x** (mismo JAR del starter). Build por defecto: BOM Boot **3.5.x**; CI también verifica `-Pboot4` (Boot **4.1.x**).

**Incluye:**

- Builder fluido sobre `JpaSpecificationExecutor` (`FluentQuery`)
- Una sola base de repositorio: `FluentQueryRepository` (CRUD + Specs + `PropertyFilters` + `query()`)
- Filtros estrictos (`where*` / `orWhere*`) y de búsqueda (`optionalWhere*`)
- Condicionales booleanos: `whereIf` / `when` / `unless`
- Grupos: `where(q → …)` / `orWhere(q → …)`
- Extractos de fecha/hora: `whereDate` / `whereYear` / `whereMonth` / `whereDay` / `whereTime` (+ `optional*`)
- Columna a columna: `whereColumn` / `orWhereColumn`; rangos: `whereBetween` / `whereNotBetween`
- Existencia de relación: `whereHas` / `whereDoesntHave` / `orWhereHas` / `orWhereDoesntHave` / `whereRelation` (`EXISTS` anidado opcional vía `RelatedFilter`)
- Sobrecargas type-safe del metamodelo (`User_.email`, …) cuando el host genera el metamodelo estático JPA
- Carga eager: `fetch` (to-one) y `fetchCollection` (to-many, con reglas claras de paginación)
- Proyección de columnas: `select(...)` (Spring Data `project`; preferible con `*As`)
- Paginación: `page` / `slice` / `paginate` / `chunk`
- Terminales: `first` / `firstOrFail` / `latest` / `oldest` / `one` / `oneOrFail` / `get` / `stream` / `exists` / `count` (+ `*OrNull` / `*As`)
- LIKE portable por defecto (`UPPER` + `LIKE`); modo Oracle unaccent opcional
- Core usable sin el starter de Boot (`spring-fluent-query-core`)

## ¿Por qué usar Fluent Query?

Spring Data ya tiene Specifications y `JpaSpecificationExecutor.findBy`, pero componer muchos filtros **opcionales** sigue siendo verboso, y patrones comunes (última fila, fetch to-one + page) son fáciles de hacer mal o caros.

**Fluent Query es una capa de DX** sobre el mismo motor: no sustituye Spring Data JPA — compone predicados y delega los terminales en la API fluida oficial.

| Sin Fluent Query | Con Fluent Query |
|------------------|------------------|
| `Specification.where(a).and(b)` manual y null checks | `query().where("a", x).where("b", y)` — **estricto** (siempre se aplica) |
| Params de búsqueda opcionales → `if (value != null)` a mano | `optionalWhere` / `optionalWhereLike` / `optionalWhereIn` … |
| `findAll(spec, PageRequest.of(0,1))` (a menudo COUNT + SELECT) | `latest("createdAt")` → LIMIT 1, **sin COUNT** |
| N+1 en asociaciones | `fetch("status")` para to-one |
| Bugs de paginación + fetch de colecciones | `fetchCollection` bloqueado con `page` / `slice` |
| Condicionales booleanos repartidos en el servicio | `whereIf` / `when` |
| Batches a mano con slices | `chunk(size, batch → …)` |

**Cuándo te bastan Specs planos:** uno o dos predicados fijos, sin filtros opcionales, sin necesidad de encadenamiento estilo Eloquent.

**Cuándo compensa Fluent Query:** endpoints de búsqueda con muchos filtros opcionales, “último por fecha”, fetch to-one con paginación, filtros por relación, procesamiento por chunks, o equipos que vienen de Laravel Eloquent.

Puedes mezclar `Specification` tipados con helpers de columna string en la misma cadena.

## Tabla de contenidos

- [¿Por qué usar Fluent Query?](#por-qué-usar-fluent-query)
- [Requisitos](#requisitos)
- [Inicio rápido](#inicio-rápido)
- [Patrón CRUD](#patrón-crud)
- [Configuración](#configuración)
- [Guía de uso](#guía-de-uso)
  - [Imports de referencia](#imports-de-referencia)
  - [Filtros estrictos (`where*`, `orWhere*`, grupos)](#filtros-estrictos-where-orwhere-grupos)
  - [Filtros opcionales (`optionalWhere*`)](#filtros-opcionales-optionalwhere)
  - [Condicionales (`whereIf`, `when`, `unless`)](#condicionales-whereif-when-unless)
  - [Comparaciones, rangos, like, in](#comparaciones-rangos-like-in)
  - [Extractos de fecha y hora](#extractos-de-fecha-y-hora)
  - [Columna a columna (`whereColumn`)](#columna-a-columna-wherecolumn)
  - [Filtros por relación](#filtros-por-relación)
  - [Metamodelo type-safe](#metamodelo-type-safe)
  - [Fetch, select, distinct, limit, order](#fetch-select-distinct-limit-order)
  - [Terminales](#terminales)
  - [Paginación y chunking](#paginación-y-chunking)
  - [Proyecciones (`as`) y `select`](#proyecciones-as-y-select)
  - [Specifications tipadas](#specifications-tipadas)
- [PropertyFilters](#propertyfilters)
- [Compatibilidad Boot 3.x / 4.x](#compatibilidad-boot-3x--4x)
- [Arquitectura de módulos](#arquitectura-de-módulos)
- [Referencia ejecutable (example)](#referencia-ejecutable-example)
- [Solución de problemas](#solución-de-problemas)
- [Referencia de API](#referencia-de-api)
- [Desarrollo](#desarrollo)
- [Roadmap](#roadmap)
- [Licencia](#licencia)

## Requisitos

- **Java 17+**
- **Spring Boot 3.x o 4.x** (mismo JAR del starter)
- **Spring Data JPA** en **tu** app (`spring-boot-starter-data-jpa` — ver abajo)

| Spring Boot | BOM por defecto en este repo | Fluent Query |
|-------------|------------------------------|--------------|
| 3.x | 3.5.16 | Soportado (CI por defecto) |
| 4.x | 4.1.0 vía `-Pboot4` | Soportado (`mvn verify -Pboot4`) |

El mismo JAR del starter sirve en ambos. El orden de auto-config usa `afterName` con FQCN de Hibernate JPA para paquetes de **Boot 3 y Boot 4**.

### ¿Qué dependencias instalo yo?

| Dependencia | ¿La añades tú? | Cuándo |
|-------------|----------------|--------|
| `spring-fluent-query-spring-boot-starter` | **Sí** | Siempre (FluentQuery + base de repo + auto-config) |
| `spring-boot-starter-data-jpa` | **Sí** | Obligatorio — JPA es **opcional** en el starter para que controles el stack |
| Driver JDBC + DataSource | Sí | Tu base de datos (H2 en tests; PostgreSQL, MySQL, Oracle, … en prod) |

**No instales por separado** (ya vienen de forma transitiva cuando hay JPA):

| Dependencia | Motivo |
|-------------|--------|
| `spring-fluent-query-core` | Incluido por el starter |
| `spring-data-jpa` | Viene con `spring-boot-starter-data-jpa` |

### Sin Spring Boot

Usa `spring-fluent-query-core` y cablea `FluentQuery.of(executor)` (o `FluentQuery.of(executor, filters)`) tú mismo. Detalles en [Arquitectura de módulos](#arquitectura-de-módulos).

## Inicio rápido

### 1. Dependencias

Añade el starter de Fluent Query **y** Spring Data JPA:

**Maven**

```xml
<dependency>
    <groupId>io.github.benjaminor-dev</groupId>
    <artifactId>spring-fluent-query-spring-boot-starter</artifactId>
    <version>0.1.1</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

**Gradle (Kotlin DSL)**

```kotlin
implementation("io.github.benjaminor-dev:spring-fluent-query-spring-boot-starter:0.1.1")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
```

**Gradle (Groovy)**

```groovy
implementation 'io.github.benjaminor-dev:spring-fluent-query-spring-boot-starter:0.1.1'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
```

**Maven multi-módulo** (mismo repositorio):

```xml
<dependency>
    <groupId>io.github.benjaminor-dev</groupId>
    <artifactId>spring-fluent-query-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

> Disponible en [Maven Central](https://search.maven.org/artifact/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter) — no hace falta configurar repositorios extra.

### 2. Extiende un solo interface de repositorio

```java
import dev.benjaminor.fluentquery.FluentQueryRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends FluentQueryRepository<User, Long> {
}
```

Ese único `extends` te da `JpaRepository`, `JpaSpecificationExecutor`, `PropertyFilters` y `query()`.

### 3. Consulta

```java
import java.util.Optional;

Optional<User> user = userRepository.query()
        .where("email", email)
        .fetch("profile")
        .latest("createdAt");
```

> Todos los bloques Java de este README incluyen **imports completos** para copiar y pegar sin adivinar el origen.


## Patrón CRUD

`FluentQueryRepository` sigue siendo un `JpaRepository` de Spring Data: las **escrituras** usan `save` / `delete`, las **lecturas** (y el borrado filtrado) usan `query()`.

```java
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Create */
    @Transactional
    public User create(String email, String name) {
        User user = new User();
        user.setEmail(email);
        user.setName(name);
        return userRepository.save(user);
    }

    /** Read */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.query()
                .where("email", email)
                .first();
    }

    /** Read (search) */
    @Transactional(readOnly = true)
    public List<User> search(String nameFragment, String status) {
        return userRepository.query()
                .optionalWhereLike("name", nameFragment)
                .optionalWhere("status", status)
                .orderByAsc("id")
                .get();
    }

    /** Update */
    @Transactional
    public Optional<User> rename(Long id, String newName) {
        return userRepository.query()
                .where("id", id)
                .first()
                .map(user -> {
                    user.setName(newName);
                    return userRepository.save(user);
                });
    }

    /**
     * Borrar <b>una</b> fila por primary key.
     * Carga con {@code first()} (o {@code one()}) y luego {@code delete(entity)} —
     * no uses el {@code delete()} masivo para una sola fila conocida.
     *
     * @return {@code true} si se borró una fila
     */
    @Transactional
    public boolean deleteById(Long id) {
        return userRepository.query()
                .where("id", id)
                .first()
                .map(user -> {
                    userRepository.delete(user);
                    return true;
                })
                .orElse(false);
        // también válido: userRepository.findById(id).ifPresent(userRepository::delete);
    }

    /**
     * Borrar <b>muchas</b> filas que cumplen un filtro.
     * {@code delete()} elimina <em>todas</em> las coincidencias — mantén el {@code where*}
     * acotado (y valora un {@code count()} / dry-run antes en herramientas de admin).
     *
     * @return número de filas borradas
     */
    @Transactional
    public long deleteAllInactive() {
        return userRepository.query()
                .where("status", "INACTIVE")
                .delete();
    }
}
```

| Operación | Llamada típica |
|-----------|----------------|
| **Create** | `repository.save(entity)` |
| **Read** | `repository.query().where(...).first()` / `get()` / `page(...)` |
| **Update** | `query().where("id", id).first()` → mutar → `save` |
| **Delete una** | `query().where("id", id).first()` → `repository.delete(entity)` (o `findById` / `deleteById`) |
| **Delete muchas** | `query().where(...).delete()` — borra **todas** las coincidencias; acota bien el filtro |

El módulo example ejecuta este flujo al arrancar (`DemoCrudService`).

## Configuración

El starter registra `SpringFluentQueryAutoConfiguration` después de Hibernate JPA (FQCN de Boot 3 y Boot 4) y aplica `spring.fluent-query.*` a `FluentQueryDefaults`.

### Modo LIKE

Por defecto LIKE es **portable** vía Criteria: `UPPER(column) LIKE %VALUE%` (funciona en H2, PostgreSQL, MySQL, SQL Server, Oracle, …).

Para plegar acentos en Oracle con `CONVERT(..., 'US7ASCII')`:

**YAML**

```yaml
spring:
  fluent-query:
    like-mode: oracle-unaccent
```

**Properties**

```properties
spring.fluent-query.like-mode=oracle-unaccent
```

| Propiedad | Default | Descripción |
|-----------|---------|-------------|
| `spring.fluent-query.like-mode` | `portable` | `portable` → `UPPER(column) LIKE %VALUE%`; `oracle-unaccent` → `UPPER(CONVERT(column, 'US7ASCII')) LIKE %VALUE%` |

`oracle-unaccent` es específico de Oracle y **no** es portable a H2 / PostgreSQL / MySQL.

No hace falta ninguna otra propiedad para el uso básico: extiende `FluentQueryRepository` y llama a `query()`.

## Guía de uso

### Imports de referencia

| Origen | Import típico | Cuándo |
|--------|---------------|--------|
| Fluent Query | `import dev.benjaminor.fluentquery.FluentQueryRepository;` | Base del repositorio |
| Fluent Query | `import dev.benjaminor.fluentquery.FluentQuery;` | Raro — preferir `repository.query()` |
| Spring Data | `import org.springframework.data.domain.*;` | `Pageable`, `Page`, `Slice`, `Sort` |
| Spring Data JPA | `import org.springframework.data.jpa.domain.Specification;` | Scopes tipados |

### Filtros estrictos (`where*`, `orWhere*`, grupos)

`where*` / `orWhere*` son **estrictos**: el predicado **siempre** se aplica.

| Llamada | Comportamiento |
|---------|----------------|
| `where(col, null)` / `whereEqual(null)` | `IS NULL` |
| `whereNotEqual(null)` | `IS NOT NULL` |
| `whereIn([])` | Disyunción (no coincide nada) |
| `whereNotIn([])` | Conjunción (siempre verdadero) |
| `whereLike(col, null)` | `NullPointerException` |

Los strings se recortan (`trim`) antes de comparar; el blank **no** se omite (usa `optionalWhere*` para eso).

```java
import java.util.List;

List<User> users = userRepository.query()
        .where("status", "ACTIVE")
        .where("deletedAt", null)   // IS NULL
        .orWhere("role", "ADMIN")
        .get();
```

**Grupo AND** — `where(q → …)` anida predicados con AND y luego ANDea el grupo a la query exterior:

```java
userRepository.query()
        .where(q -> q.where("a", 1).where("b", 2))
        .get();
// … AND (a = 1 AND b = 2)
```

**Grupo OR** — `orWhere(q → …)` → `… OR (a AND b)`:

```java
userRepository.query()
        .where("tenantId", tenantId)
        .orWhere(q -> q.where("email", email).where("verified", true))
        .first();
```

### Filtros opcionales (`optionalWhere*`)

Úsalos en **endpoints de búsqueda**: si el valor falta (`null` / blank / colección vacía), el predicado es un **no-op**.

```java
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

Page<User> page = userRepository.query()
        .optionalWhere("status", status)
        .optionalWhereLike("name", search)
        .optionalWhereIn("role", roles)
        .optionalWhereGte("createdAt", from)
        .optionalWhereLte("createdAt", to)
        .page(PageRequest.of(0, 20));
```

También: `optionalWhereEqual`, `optionalWhereContains` / `StartsWith` / `EndsWith` / `LikePattern`, `optionalOrWhere` / `optionalOrWhereLike` / `optionalOrWhereIn` / `optionalOrWhereNotEqual` / `optionalOrWhereNotIn`, `optionalWhereNotEqual`, `optionalWhereRelatedEqual`, `optionalWhereRelatedLike`, `optionalWhereNotIn`, `optionalWhereGt` / `Gte` / `Lt` / `Lte` (alias largos `optionalWhereGreaterThan*` / `LessThan*`), `optionalWhereBetween` / `NotBetween`, `optionalWhereDate` / `Year` / `Month` / `Day` / `Time`, `optionalWhereRelation`.

`whereIf` / `when` / `unless` son distintos: reciben un **booleano** explícito, no “¿hay valor?”.

### Condicionales (`whereIf`, `when`, `unless`)

```java
import java.util.List;

List<User> users = userRepository.query()
        .whereIf(includeInactive, "status", "INACTIVE")
        .when(hasSearch, q -> q.optionalWhereLike("name", search))
        .unless(isAdmin, q -> q.where("tenantId", tenantId))  // when(!isAdmin, …)
        .when(includeInactive,
                q -> q.whereIn("status", List.of("ACTIVE", "INACTIVE")),
                q -> q.where("status", "ACTIVE"))             // then / else
        .orderByAsc("name")
        .get();
```

### Comparaciones, rangos, like, in

```java
import java.util.List;

List<Order> orders = orderRepository.query()
        .whereGt("total", minTotal)
        .whereGte("createdAt", from)
        .whereLte("createdAt", to)
        .whereBetween("amount", low, high)      // extremos abiertos permitidos (solo from / solo to)
        .whereNotBetween("amount", 0, 10)       // ambos extremos requeridos
        .whereLike("customerName", term)        // non-null/non-blank; wildcards permitidos
        .whereContains("notes", "100%")         // texto libre; %/_ escapados
        .whereStartsWith("sku", "AB")
        .whereEndsWith("email", "@acme.com")
        .whereLikePattern("code", "_X%")        // patrón raw
        .whereIn("status", List.of("NEW", "PAID"))
        .whereNotNull("paidAt")
        .get();
```

Alias: `whereGt` / `whereGte` / `whereLt` / `whereLte` mapean a los métodos largos `whereGreaterThan*` / `whereLessThan*` (mismo emparejamiento corto/largo en `optionalWhere*`).

**Reglas LIKE** (también en `RelatedFilter` / `whereHas` anidado):

| API | Comportamiento |
|-----|----------------|
| `whereLike` | Sin `%`/`_` → `%VALUE%`; si hay wildcards → patrón raw |
| `whereContains` / `StartsWith` / `EndsWith` | Escapado (`ESCAPE '\'`); preferible para texto libre de UI |
| `whereLikePattern` | Patrón tal cual (trim + upper); tú pones los wildcards |
| Variantes `optionalWhere*` | Blank → no-op |

La estrategia LIKE sigue [`spring.fluent-query.like-mode`](#configuración) (default `portable`). El LIKE estricto rechaza strings en blanco (`IllegalArgumentException`); usa `optionalWhere*` para params de búsqueda.

### Extractos de fecha y hora

Usa Criteria `cb.function` con nombres en minúsculas que Hibernate mapea de forma portable (`year`, `month`, `day`, `hour`, `minute`, `second`) en dialectos H2 / PostgreSQL / MySQL / Oracle.

```java
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

List<Order> orders = orderRepository.query()
        .whereYear("createdAt", 2024)
        .whereMonth("createdAt", 7)                          // 1–12
        .whereDate("createdAt", LocalDate.of(2024, 7, 15))   // year+month+day
        .whereTime("createdAt", LocalTime.of(10, 30, 0))     // hour+minute+second
        .get();
```

Variantes opcionales (`optionalWhereDate` / `Year` / `Month` / `Day` / `Time`) omiten el predicado si el valor es `null`. Los mismos helpers existen en `RelatedFilter`.

### Columna a columna (`whereColumn`)

```java
import java.util.List;

List<Author> authors = authorRepository.query()
        .whereColumn("score", "threshold")           // igualdad
        .whereColumn("score", ">", "threshold")      // = != <> > >= < <=
        .orWhereColumn("score", "threshold")
        .get();
```

`<>` se normaliza a `!=`. Operadores inválidos lanzan `IllegalArgumentException`. Metamodelo: `whereColumn(SingularAttribute, SingularAttribute)` (+ overload con operador).

### Filtros por relación

```java
import java.util.List;

List<User> users = userRepository.query()
        .whereRelatedEqual("company", "id", companyId)
        .whereRelatedLike("company", "name", nameFragment)
        .whereHas("orders")
        .whereDoesntHave("deletedProfile")
        .whereRelation("orders", "status", "OPEN")   // = whereHas(rel, f -> f.where(col, val))
        .get();
```

| Método | Significado |
|--------|-------------|
| `whereHas(relation)` | Asociación presente (colección → `IS NOT EMPTY`; to-one → `IS NOT NULL`) |
| `whereDoesntHave(relation)` | Asociación ausente (colección → `IS EMPTY`; to-one → `IS NULL`) |
| `whereHas(relation, f -> …)` | `EXISTS` anidado con predicados sobre la entidad relacionada (`RelatedFilter`) |
| `whereDoesntHave(relation, f -> …)` | `NOT EXISTS` anidado con la misma API de filtro |
| `orWhereHas` / `orWhereDoesntHave` | Variantes OR (simple o anidada) |
| `whereRelation` / `optionalWhereRelation` | Atajo de igualdad en columna relacionada (`optional*` omite blank/null) |

```java
import java.util.List;

// Autores con al menos un libro de más de 100 páginas
List<Author> authors = authorRepository.query()
        .whereHas("books", f -> f
                .whereGt("pages", 100)
                .optionalWhereLike("title", titleFragment))
        .orWhereHas("books", f -> f.where("title", "Featured"))
        .get();
```

`RelatedFilter` refleja el builder principal para predicados anidados: `where` / `whereEqual` / `whereLike` / `whereContains` / `whereStartsWith` / `whereEndsWith` / `whereLikePattern` / `whereIn` / `whereNotIn` / comparaciones (`whereGt`… + formas largas) / rangos / extractos de fecha / familia completa `optionalWhere*` (incluye opcionales LIKE escapados), etc. El LIKE anidado también respeta `spring.fluent-query.like-mode`.

### Metamodelo type-safe

Cuando el proyecto host genera el **metamodelo estático JPA** (`User_`, `Order_`, …), `FluentQuery` ofrece sobrecargas con `SingularAttribute` / `PluralAttribute` que delegan a las APIs string vía `attribute.getName()`.

La cobertura incluye igualdad, familia LIKE (estricta + opcional, incluyendo Contains/StartsWith/EndsWith/LikePattern), In/NotIn/Null, comparaciones, rangos, extractos de fecha, `whereColumn` / `orWhereColumn`, related equal/like (+ opcional), existencia de relación (`whereHas` / …), order y fetch.

```java
import java.util.Optional;

Optional<User> user = userRepository.query()
        .where(User_.email, email)
        .optionalWhereLike(User_.name, nameFragment)
        .optionalWhereStartsWith(User_.sku, skuPrefix)
        .orderByDesc(User_.createdAt)
        .fetch(User_.profile)
        .first();

List<Author> withLongBooks = authorRepository.query()
        .whereHas(Author_.books, f -> f.whereGt("pages", 100))
        .get();
```

Activa la generación del metamodelo en el host (annotation processor / Hibernate JPamodelgen, o el equivalente de tu stack). La librería no publica clases `*_` generadas.

### Fetch, select, distinct, limit, order

| Método | Uso | Con `page` / `slice` / `paginate` / `chunk` |
|--------|-----|---------------------------------------------|
| `fetch("profile")` | To-one (`@ManyToOne` / `@OneToOne`) | ✅ Seguro |
| `fetchCollection("orders")` | To-many (`@OneToMany` / `@ManyToMany`) | ❌ Lanza `IllegalStateException` |
| `select("id", "email")` | Proyección de propiedades (`project`); preferible con `*As` | ✅ |
| `distinct()` | Forzar DISTINCT | ✅ |
| `limit(n)` | Tope de filas en `get()` / `limit` de Spring | Se aplica vía pageable cuando conviene |
| `orderByAsc` / `orderByDesc` / `orderBy(Sort)` | Orden | Se fusiona en el pageable si no hay sort |

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

Page<User> page = userRepository.query()
        .where("active", true)
        .fetch("status")                 // solo to-one
        .orderByDesc("createdAt")
        .page(PageRequest.of(0, 20));
```

```java
import org.springframework.data.domain.PageRequest;

// MAL — producto cartesiano / COUNT incorrecto
userRepository.query()
        .fetchCollection("orders")
        .page(PageRequest.of(0, 20));   // IllegalStateException
```

Prefiere cargar colecciones en una **segunda query**, o usa `get()` / `first()` sin paginación cuando realmente necesites fetch de colección.

### Terminales

| Terminal | Resultado | Notas |
|----------|-----------|-------|
| `first()` | `Optional<T>` | Primer match (`LIMIT 1`); OK si hay varios; **sin COUNT** |
| `firstOrFail()` | `T` | Lanza `FluentQueryNotFoundException` si vacío (azúcar opcional) |
| `firstOrNull()` | `T` o `null` | |
| `latest(property)` | `Optional<T>` | `orderByDesc` + `first` |
| `oldest(property)` | `Optional<T>` | `orderByAsc` + `first` |
| `one()` | `Optional<T>` | Espera 0–1 fila; Spring Data lanza si hay **2+** |
| `oneOrFail()` | `T` | Lanza `FluentQueryNotFoundException` si vacío (azúcar opcional) |
| `get()` | `List<T>` | Respeta `limit`; sin limit puede cargar toda la tabla |
| `page(pageable)` | `Page<T>` | Con COUNT; sin `fetchCollection` |
| `paginate(page, size)` | `Page<T>` | Índice 0-based + size |
| `slice(pageable)` | `Slice<T>` | **Sin** COUNT — mejor para scroll infinito |
| `chunk(size, consumer)` | `void` | Batches vía `slice` (sin COUNT) |
| `stream()` | `Stream<T>` | **Hay que** cerrarlo (`try-with-resources`) |
| `exists()` | `boolean` | Solo predicados (sin fetch) |
| `count()` | `long` | Solo predicados (sin fetch) |

```java
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

Optional<User> latest = userRepository.query()
        .where("email", email)
        .latest("createdAt");

User maybe = userRepository.query()
        .where("email", email)
        .latestOrNull("createdAt");

Slice<User> next = userRepository.query()
        .where("active", true)
        .orderByDesc("createdAt")
        .slice(PageRequest.of(0, 20));

boolean any = userRepository.query().where("email", email).exists();
long total = userRepository.query().where("active", true).count();

try (Stream<User> stream = userRepository.query()
        .where("active", true)
        .orderByAsc("id")
        .stream()) {
    stream.forEach(this::process);
}
```

**Contrato del builder:** cada builder de `query()` es de **un solo uso** y **no es thread-safe**. No compartas una instancia entre hilos ni la reutilices tras un terminal.

### Paginación y chunking

```java
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

Page<User> byPageable = userRepository.query()
        .optionalWhere("status", status)
        .orderByDesc("createdAt")
        .page(PageRequest.of(0, 20));

Page<User> byIndex = userRepository.query()
        .optionalWhere("status", status)
        .orderByDesc("createdAt")
        .paginate(0, 20);   // índice de página, size

userRepository.query()
        .where("active", true)
        .orderByAsc("id")
        .chunk(100, batch -> {
            // procesar cada batch no vacío
        });
```

`chunk` usa `slice` por debajo (sin COUNT). Prefiere un `orderBy*` estable para no saltar ni duplicar filas.

### Proyecciones (`as`) y `select`

Usa `SpecificationFluentQuery.as(Class)` de Spring Data — proyecciones interface/DTO **sin** join-fetch de entidad.

`select("col1", "col2")` es el alias estilo Eloquent de Spring Data `project(...)`:

| Combinación | Qué obtienes |
|-------------|--------------|
| `select(...).getAs(Projection.class)` | **Preferido** — limita propiedades proyectadas (SQL más liviano en interface/DTO) |
| `select(...).get()` (entidad) | Aplica reglas `project` / EntityGraph de Spring Data; JPA **no** puede devolver una “entidad parcial” como Eloquent |

Si quieres un nombre personalizado en el resultado, expónlo en el tipo de proyección y deja el `select` con la ruta del atributo de entidad:

```java
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

public interface UserSummary {
    Long getId();
    String getEmail();
    // Nombre API personalizado: mapea desde la propiedad de entidad "email"
    // (p. ej. getEmailPersonalizado() + @Value / mapeo DTO según necesites)
}

Optional<UserSummary> first = userRepository.query()
        .where("active", true)
        .select("id", "email")
        .firstAs(UserSummary.class);

List<UserSummary> all = userRepository.query()
        .whereLike("email", "@example.com")
        .select(List.of("id", "email"))
        .limit(100)
        .getAs(UserSummary.class);

Page<UserSummary> page = userRepository.query()
        .select("id", "email")
        .orderByDesc("createdAt")
        .pageAs(UserSummary.class, PageRequest.of(0, 20));
```

También: `select(User_.id, User_.email)` si el metamodelo estático está disponible.

### Specifications tipadas

Prefiere Specs tipados para reglas de dominio; usa `where("col", val)` para igualdad simple:

```java
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

Specification<User> activeInTenant = (root, query, cb) -> cb.and(
        cb.isTrue(root.get("active")),
        cb.equal(root.get("tenantId"), tenantId)
);

userRepository.query()
        .where(activeInTenant)
        .whereLike("name", search)
        .page(pageable);
```

También puedes extraer el Spec compuesto sin ejecutar:

```java
import org.springframework.data.jpa.domain.Specification;

Specification<User> spec = userRepository.query()
        .where("active", true)
        .whereLike("name", search)
        .toSpecification();
```

## PropertyFilters

`FluentQueryRepository` ya extiende `PropertyFilters`. Al llamar a `query()`, el builder los cablea automáticamente.

Igualdad, comparaciones, null checks, helpers de relación y LIKE pasan por `PropertyFilters` cuando están presentes. LIKE sigue [`spring.fluent-query.like-mode`](#configuración) (default **`portable`**).

Opciones avanzadas:

- Implementar solo `PropertyFilters` en un repositorio custom
- Llamar a `FluentQuery.of(executor)` (sin filtros ricos) o `FluentQuery.of(executor, filters)` a mano

## Compatibilidad Boot 3.x / 4.x

| Mecanismo | Detalle |
|-----------|---------|
| Un solo artefacto | Un JAR para Boot 3 y 4 |
| BOM por defecto | Spring Boot **3.5.16** |
| Perfil `boot4` | `mvn verify -Pboot4` → BOM Boot **4.1.0** |
| Orden de auto-config | `afterName` lista FQCN de Hibernate JPA para **ambos** paquetes Boot 3 y Boot 4 |
| API Spring Data | Usa `JpaSpecificationExecutor.findBy` + `SpecificationFluentQuery` (Data JPA 3.x+) |

```bash
mvn clean verify          # BOM Boot 3.x
mvn clean verify -Pboot4  # BOM Boot 4.x
```

## Arquitectura de módulos

```text
spring-fluent-query/
├── spring-fluent-query-core/                 # Publicable. Sin auto-config de Boot.
│   ├── FluentQuery                           # Builder + terminales
│   ├── FluentQueryRepository                 # Base de repo + query()
│   ├── PropertyFilters                       # Helpers ricos de Spec
│   ├── LikeMode / FluentQueryDefaults        # Estrategia LIKE
│   └── support/                              # Joins, Values, …
│
├── spring-fluent-query-spring-boot-starter/  # Publicable. Auto-config.
│   └── autoconfigure/
│       ├── SpringFluentQueryAutoConfiguration
│       └── SpringFluentQueryProperties       # spring.fluent-query.*
│
└── spring-fluent-query-example/              # No publicable. Demo mínima.
```

| Artefacto Maven | Cuándo usarlo |
|-----------------|---------------|
| `spring-fluent-query-spring-boot-starter` | Apps Spring Boot (recomendado) |
| `spring-fluent-query-core` | Librerías / cableado custom sin auto-config de Boot |

**Auto-configuración incluida en el starter:**

| Clase | Responsabilidad |
|-------|-----------------|
| `SpringFluentQueryAutoConfiguration` | Tras Hibernate JPA (Boot 3 + 4); aplica `like-mode` a `FluentQueryDefaults` |
| `SpringFluentQueryProperties` | Bind de `spring.fluent-query.like-mode` |

## Referencia ejecutable (example)

El módulo **`spring-fluent-query-example`** es una app mínima ejecutable (H2 + `FluentQueryRepository`).

```bash
docker compose up example   # arranca spring-fluent-query-example en :8080
```

O sin Docker:

```bash
mvn -pl spring-fluent-query-example spring-boot:run
```

Al arrancar ejecuta un flujo completo **Create / Read / Update / Delete** con `DemoCrudService` (ver logs).

El módulo example también incluye cobertura H2 `@DataJpaTest` (`FluentQueryDataJpaIT`) para `whereHas` anidado, extractos de fecha, `whereColumn`, `whereNotBetween`, `orWhereHas`, `whereRelation`, `unless`, `firstOrFail` / `oneOrFail`, `paginate`, `select` + `firstAs`, optionals, `delete()` y rechazo de `fetchCollection` + `page`.

## Solución de problemas

### `query()` no está disponible en mi repositorio

Extiende `FluentQueryRepository<Entity, Id>` (no solo `JpaRepository`). Un `extends` basta — no hace falta un segundo interface para `PropertyFilters`.

### `fetchCollection` + `page` / `slice` / `paginate` / `chunk` lanza

Esperado: el fetch de colección con paginación es inseguro (producto cartesiano / COUNT incorrecto). Usa `fetch()` para to-one, o carga colecciones en una segunda query.

### `get()` es lento / OOM

Sin `limit`, `get()` puede cargar toda la tabla. Prefiere `page`, `slice`, `paginate`, `chunk` o `limit`.

### LIKE falla en H2 / PostgreSQL con sintaxis Oracle

El modo por defecto es **`portable`** (`UPPER` + `LIKE`). Si configuras `spring.fluent-query.like-mode=oracle-unaccent`, Criteria usa `CONVERT(..., 'US7ASCII')` de Oracle, que otras bases rechazan. Usa `portable` salvo que corras en Oracle y necesites plegado de acentos.

### `whereLike` rechaza null/blank

Los `whereLike` / `whereContains` / `whereStartsWith` / `whereEndsWith` / `whereLikePattern` estrictos exigen un valor non-null y non-blank (`NullPointerException` / `IllegalArgumentException`). Para params de búsqueda que pueden venir vacíos, usa el `optionalWhere*` correspondiente.

### Wildcards LIKE (`%`, `_`)

- `whereLike("name", "ada")` → `%ADA%` (contains)
- `whereLike("name", "ADA%")` → prefijo (patrón raw porque hay `%`)
- `whereContains("name", "100%")` → `%` literal en el valor (escapado); preferible para búsqueda libre de UI
- `whereLikePattern("name", "_X%")` → patrón exactamente como se dio (trim + upper)

### Nombres personalizados en proyecciones

Si necesitas un nombre personalizado en el resultado de la API, defínelo en la interface/DTO de proyección y haz `select` de la ruta del atributo de entidad (p. ej. `select("email").getAs(UserSummary.class)` con `getEmailPersonalizado()` / el mapeo correspondiente).

### La auto-config no corre en Boot 4

Asegúrate de usar el starter publicado (archivo de imports bajo `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) y de que `JpaSpecificationExecutor` esté en el classpath (`spring-boot-starter-data-jpa`).

## Referencia de API

### Filtros

| Método | Descripción |
|--------|-------------|
| `where(Specification)` | AND de Spec tipado (`null` Spec se ignora) |
| `where(column, value)` | Igualdad estricta; `null` → `IS NULL` |
| `where(Consumer)` | Grupo AND |
| `optionalWhere*` | No-op si falta el valor: `optionalWhere` / `Equal`, `Like` / `Contains` / `StartsWith` / `EndsWith` / `LikePattern`, `In` / `NotIn`, `NotEqual`, `Gt`/`Gte`/`Lt`/`Lte` (+ alias largos), `Between` / `NotBetween`, extractos de fecha, `Related*` / `Relation`; OR: `optionalOrWhere` / `Like` / `In` / `NotEqual` / `NotIn` |
| `whereIf` / `when` / `unless` | Condicionales booleanos; `when` soporta then/else (Eloquent); `unless` = `when(!condition, …)` |
| `orWhere(...)` | OR Spec / igualdad / grupo |
| `whereNot` | NOT Spec |
| `whereEqual` / `whereEqualIf` / `whereNotEqual` | Igualdad / desigualdad explícitas |
| `whereRelatedEqual` / `whereRelatedLike` | Join + filtro (reglas `toPattern` para Like) |
| `whereLike` | LIKE case-insensitive; sin `%`/`_` → contains (`%VALUE%`); si el valor tiene `%`/`_` → patrón raw; rechaza blank |
| `whereContains` / `whereStartsWith` / `whereEndsWith` | LIKE de texto libre escapado (`%`/`_` literales); preferible para búsqueda de usuario |
| `whereLikePattern` | Patrón raw tal cual (tú pones los wildcards) |
| `whereIn` / `whereNotIn` | Pertenencia; vacío → disyunción / conjunción |
| `whereNull` / `whereNotNull` | Chequeos de null |
| `whereGt` / `whereGte` / `whereLt` / `whereLte` | Comparaciones (alias largos `whereGreaterThan*` / `whereLessThan*`) |
| `whereBetween` / `whereNotBetween` | Rango inclusivo / rango negado |
| `whereDate` / `whereYear` / `whereMonth` / `whereDay` / `whereTime` | Extractos temporales (`cb.function`) |
| `whereColumn` / `orWhereColumn` | Comparación columna a columna |
| `whereHas` / `whereDoesntHave` / `orWhereHas` / `orWhereDoesntHave` | Asociación existe / ausente (`EXISTS` anidado opcional) |
| `whereRelation` / `optionalWhereRelation` | Atajo de igualdad en columna relacionada |
| Sobrecargas metamodelo | APIs string de arriba con `SingularAttribute` / `PluralAttribute` (filtros, related, has/doesntHave, `select`, order, fetch) |
| `of(executor)` / `of(executor, filters)` / `query()` | Factory / puntos de entrada del repositorio |

### Carga / forma

| Método | Descripción |
|--------|-------------|
| `fetch` | LEFT JOIN FETCH to-one; activa DISTINCT |
| `fetchCollection` | LEFT JOIN FETCH to-many; **no** con page/slice/paginate/chunk |
| `select` | Proyección de propiedades estilo Eloquent (`project`); mejor con `*As` |
| `distinct` | Forzar DISTINCT |
| `limit` | Máximo de filas en terminales de lista |
| `orderByAsc` / `orderByDesc` / `orderBy` | Orden |

### Terminales

| Método | Descripción |
|--------|-------------|
| `first` / `firstOrNull` / `firstOrFail` | Primera fila (`LIMIT 1`); OK si hay varias; `*OrFail` lanza si vacío |
| `latest` / `latestOrNull` | `orderByDesc` + first |
| `oldest` / `oldestOrNull` | `orderByAsc` + first |
| `one` / `oneOrNull` / `oneOrFail` | Exactamente 0–1 fila; **lanza si hay 2+** (`IncorrectResultSizeDataAccessException`); `*OrFail` lanza si vacío |
| `get` | Lista (respeta `limit`) |
| `page` | Página con COUNT |
| `paginate` | Página por índice 0-based + size |
| `slice` | Slice sin COUNT |
| `chunk` | Batch vía slice |
| `stream` | Stream closable |
| `firstAs` / `getAs` / `pageAs` | Proyecciones |
| `exists` / `count` | Agregados sin fetch |
| `delete` | Borra filas coincidentes (`CrudRepository#deleteAll`) |
| `toSpecification` / `toSelectSpecification` / `toSort` | Inspeccionar composición |

## Desarrollo

```text
spring-fluent-query/
├── spring-fluent-query-core/
├── spring-fluent-query-spring-boot-starter/
└── spring-fluent-query-example/    ← demo mínima
```

```bash
# Build y todos los tests (BOM Boot 3)
docker compose run --rm maven

# BOM Boot 4
docker compose run --rm maven mvn clean verify -Pboot4

# Solo tests del core
docker compose run --rm maven mvn -pl spring-fluent-query-core test

# App de ejemplo
docker compose up example

# Install a .m2 local
docker compose run --rm maven mvn clean install
```

Sin Docker:

```bash
mvn clean verify
mvn clean verify -Pboot4
mvn -pl spring-fluent-query-example spring-boot:run
```

Los releases se publican en Maven Central — ver [PUBLISHING.md](PUBLISHING.md) (maintainers).

## Roadmap

- Módulo example más rico (REST + queries de muestra)
- `autoPublish=true` en Central Portal cuando la automatización de release esté estable

## Licencia

Copyright © 2026 **Benjamín Olvera R.**

Licenciado bajo la [Apache License, Version 2.0](LICENSE).
