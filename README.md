# Spring Fluent Query

[🇪🇸 Versión en español](README.es.md) | [🇧🇷 Versão em português](README.pt.md)

[![Java](https://img.shields.io/badge/Java-17+-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-3.x%20%7C%204.x-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-data-jpa)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter?label=Maven%20Central)](https://search.maven.org/artifact/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

**Eloquent-style** fluent queries on top of **Spring Data JPA Specifications** — without Active Record.

Spring Fluent Query adds a readable chain (`where` → `fetch` → `latest` / `first` / `page`) while **execution** delegates to Spring Data’s official `findBy(spec, q → …)` fluent API. That means `first()` / `latest()` use `LIMIT 1` **without** an unnecessary COUNT.

> Compatible with **Spring Boot 3.x and 4.x** (same starter JAR). Default build uses Boot **3.5.x** BOM; CI also verifies with `-Pboot4` (Boot **4.1.x** BOM).

**Includes:**

- Fluent builder over `JpaSpecificationExecutor` (`FluentQuery`)
- One repository base: `FluentQueryRepository` (CRUD + Specs + `PropertyFilters` + `query()`)
- Strict filters (`where*` / `orWhere*`) and search filters (`optionalWhere*`)
- Boolean conditionals: `whereIf` / `when` / `unless`
- Groups: `where(q → …)` / `orWhere(q → …)`
- Date/time extracts: `whereDate` / `whereYear` / `whereMonth` / `whereDay` / `whereTime` (+ `optional*`)
- Column-to-column: `whereColumn` / `orWhereColumn`; ranges: `whereBetween` / `whereNotBetween`
- Relation existence: `whereHas` / `whereDoesntHave` / `orWhereHas` / `orWhereDoesntHave` / `whereRelation` (optional nested `EXISTS` via `RelatedFilter`)
- Type-safe metamodel overloads (`User_.email`, …) when the host generates the JPA static metamodel
- Eager loading: `fetch` (to-one) and `fetchCollection` (to-many, with clear pagination rules)
- Column projection: `select(...)` (Spring Data `project`; prefer with `*As`)
- Pagination helpers: `page` / `slice` / `paginate` / `chunk`
- Terminals: `first` / `firstOrFail` / `latest` / `oldest` / `one` / `oneOrFail` / `get` / `stream` / `exists` / `count` (+ `*OrNull` / `*As`)
- Portable LIKE by default (`UPPER` + `LIKE`); optional Oracle unaccent mode
- Core usable without the Boot starter (`spring-fluent-query-core`)

## Why use Fluent Query?

Spring Data already has Specifications and `JpaSpecificationExecutor.findBy`, but composing many **optional** filters stays verbose, and common patterns (latest row, to-one fetch + page) are easy to get wrong or expensive.

**Fluent Query is a DX layer** on the same engine: it does not replace Spring Data JPA — it composes predicates and delegates terminals to the official fluent API.

| Without Fluent Query | With Fluent Query |
|----------------------|-------------------|
| Manual `Specification.where(a).and(b)` and null checks | `query().where("a", x).where("b", y)` — **strict** (always applied) |
| Optional search params → hand-rolled `if (value != null)` | `optionalWhere` / `optionalWhereLike` / `optionalWhereIn` … |
| `findAll(spec, PageRequest.of(0,1))` (often COUNT + SELECT) | `latest("createdAt")` → LIMIT 1, **no COUNT** |
| N+1 on associations | `fetch("status")` for to-one |
| Pagination + collection fetch bugs | `fetchCollection` blocked with `page` / `slice` |
| Boolean conditionals scattered in service code | `whereIf` / `when` |
| Batching rows by hand with slices | `chunk(size, batch → …)` |

**When plain Specs are enough:** one or two fixed predicates, no optional filters, no need for Eloquent-style chaining.

**When Fluent Query pays off:** search endpoints with many optional filters, “latest by date”, to-one fetch with paging, relation filters, chunked processing, or teams coming from Laravel Eloquent.

You can still mix typed `Specification`s with string-column helpers on the same chain.

## Table of contents

- [Why use Fluent Query?](#why-use-fluent-query)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [CRUD pattern](#crud-pattern)
- [Configuration](#configuration)
- [Usage guide](#usage-guide)
  - [Reference imports](#reference-imports)
  - [Strict filters (`where*`, `orWhere*`, groups)](#strict-filters-where-orwhere-groups)
  - [Optional filters (`optionalWhere*`)](#optional-filters-optionalwhere)
  - [Conditionals (`whereIf`, `when`, `unless`)](#conditionals-whereif-when-unless)
  - [Comparisons, ranges, like, in](#comparisons-ranges-like-in)
  - [Date and time extracts](#date-and-time-extracts)
  - [Column-to-column (`whereColumn`)](#column-to-column-wherecolumn)
  - [Relation filters](#relation-filters)
  - [Type-safe metamodel](#type-safe-metamodel)
  - [Fetch, select, distinct, limit, order](#fetch-select-distinct-limit-order)
  - [Terminals](#terminals)
  - [Pagination and chunking](#pagination-and-chunking)
  - [Projections (`as`) and `select`](#projections-as-and-select)
  - [Typed Specifications](#typed-specifications)
- [PropertyFilters](#propertyfilters)
- [Boot 3.x / 4.x compatibility](#boot-3x--4x-compatibility)
- [Module architecture](#module-architecture)
- [Executable reference (example)](#executable-reference-example)
- [Troubleshooting](#troubleshooting)
- [API reference](#api-reference)
- [Development](#development)
- [Roadmap](#roadmap)
- [License](#license)

## Requirements

- **Java 17+**
- **Spring Boot 3.x or 4.x** (same starter JAR)
- **Spring Data JPA** in **your** app (`spring-boot-starter-data-jpa` — see below)

| Spring Boot | Default BOM in this repo | Fluent Query |
|-------------|--------------------------|--------------|
| 3.x | 3.5.16 | Supported (default CI) |
| 4.x | 4.1.0 via `-Pboot4` | Supported (`mvn verify -Pboot4`) |

The same starter JAR works on both. Auto-config ordering uses `afterName` with Hibernate JPA FQCNs for **Boot 3 and Boot 4** packages.

### Which dependencies do I install?

| Dependency | Do you add it? | When |
|------------|----------------|------|
| `spring-fluent-query-spring-boot-starter` | **Yes** | Always (FluentQuery + repo base + auto-config) |
| `spring-boot-starter-data-jpa` | **Yes** | Required — JPA is **optional** on the starter so you keep control of the stack |
| JDBC driver + DataSource | Yes | Your database (H2 in tests; PostgreSQL, MySQL, Oracle, … in prod) |

**Do not install separately** (already pulled transitively by the starter / core when JPA is present):

| Dependency | Reason |
|------------|--------|
| `spring-fluent-query-core` | Included by the starter |
| `spring-data-jpa` | Comes with `spring-boot-starter-data-jpa` |

### Without Spring Boot

Use `spring-fluent-query-core` and wire `FluentQuery.of(executor)` (or `FluentQuery.of(executor, filters)`) yourself. Details in [Module architecture](#module-architecture).

## Quick start

### 1. Dependencies

Add the Fluent Query starter **and** Spring Data JPA:

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

**Multi-module Maven** (same repository):

```xml
<dependency>
    <groupId>io.github.benjaminor-dev</groupId>
    <artifactId>spring-fluent-query-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

> Available on [Maven Central](https://search.maven.org/artifact/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter) — no extra repository configuration needed.

### 2. Extend one repository interface

```java
import dev.benjaminor.fluentquery.FluentQueryRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends FluentQueryRepository<User, Long> {
}
```

That single `extends` gives you `JpaRepository`, `JpaSpecificationExecutor`, `PropertyFilters`, and `query()`.

### 3. Query

```java
import java.util.Optional;

Optional<User> user = userRepository.query()
        .where("email", email)
        .fetch("profile")
        .latest("createdAt");
```

> All Java blocks in this README include **full imports** so you can copy and paste without guessing the origin.


## CRUD pattern

`FluentQueryRepository` is still a Spring Data `JpaRepository`: **writes** use `save` / `delete`, **reads** (and filtered bulk delete) use `query()`.

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
     * Delete <b>one</b> row by primary key.
     * Load with {@code first()} (or {@code one()}) then {@code delete(entity)} —
     * never use bulk {@code delete()} for a single known row.
     *
     * @return {@code true} if a row was deleted
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
        // also fine: userRepository.findById(id).ifPresent(userRepository::delete);
    }

    /**
     * Delete <b>many</b> rows matching a filter.
     * {@code delete()} removes <em>every</em> match — keep the {@code where*} tight
     * (and consider {@code count()} / a dry-run first in admin tools).
     *
     * @return number of deleted rows
     */
    @Transactional
    public long deleteAllInactive() {
        return userRepository.query()
                .where("status", "INACTIVE")
                .delete();
    }
}
```

| Operation | Typical call |
|-----------|----------------|
| **Create** | `repository.save(entity)` |
| **Read** | `repository.query().where(...).first()` / `get()` / `page(...)` |
| **Update** | `query().where("id", id).first()` → mutate → `save` |
| **Delete one** | `query().where("id", id).first()` → `repository.delete(entity)` (or `findById` / `deleteById`) |
| **Delete many** | `query().where(...).delete()` — deletes **all** matches; scope the filter carefully |

The runnable example module walks through this flow on startup (`DemoCrudService`).

## Configuration

The starter registers `SpringFluentQueryAutoConfiguration` after Hibernate JPA (Boot 3 and Boot 4 FQCNs) and applies `spring.fluent-query.*` to `FluentQueryDefaults`.

### LIKE mode

By default LIKE is **portable** Criteria: `UPPER(column) LIKE %VALUE%` (works on H2, PostgreSQL, MySQL, SQL Server, Oracle, …).

For Oracle accent folding via `CONVERT(..., 'US7ASCII')`:

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

| Property | Default | Description |
|----------|---------|-------------|
| `spring.fluent-query.like-mode` | `portable` | `portable` → `UPPER(column) LIKE %VALUE%`; `oracle-unaccent` → `UPPER(CONVERT(column, 'US7ASCII')) LIKE %VALUE%` |

`oracle-unaccent` is Oracle-specific and is **not** portable to H2 / PostgreSQL / MySQL.

No other properties are required for basic use — extend `FluentQueryRepository` and call `query()`.

## Usage guide

### Reference imports

| Source | Typical import | When |
|--------|----------------|------|
| Fluent Query | `import dev.benjaminor.fluentquery.FluentQueryRepository;` | Repository base |
| Fluent Query | `import dev.benjaminor.fluentquery.FluentQuery;` | Rare — prefer `repository.query()` |
| Spring Data | `import org.springframework.data.domain.*;` | `Pageable`, `Page`, `Slice`, `Sort` |
| Spring Data JPA | `import org.springframework.data.jpa.domain.Specification;` | Typed scopes |

### Strict filters (`where*`, `orWhere*`, groups)

`where*` / `orWhere*` are **strict**: the predicate **always** applies.

| Call | Behaviour |
|------|-----------|
| `where(col, null)` / `whereEqual(null)` | `IS NULL` |
| `whereNotEqual(null)` | `IS NOT NULL` |
| `whereIn([])` | Disjunction (matches nothing) |
| `whereNotIn([])` | Conjunction (always true) |
| `whereLike(col, null)` | `NullPointerException` |

Strings are trimmed before comparison; blank is **not** skipped (use `optionalWhere*` for that).

```java
import java.util.List;

List<User> users = userRepository.query()
        .where("status", "ACTIVE")
        .where("deletedAt", null)   // IS NULL
        .orWhere("role", "ADMIN")
        .get();
```

**AND group** — `where(q → …)` nests predicates with AND, then ANDs the group into the outer query:

```java
userRepository.query()
        .where(q -> q.where("a", 1).where("b", 2))
        .get();
// … AND (a = 1 AND b = 2)
```

**OR group** — `orWhere(q → …)` → `… OR (a AND b)`:

```java
userRepository.query()
        .where("tenantId", tenantId)
        .orWhere(q -> q.where("email", email).where("verified", true))
        .first();
```

### Optional filters (`optionalWhere*`)

Use these for **search endpoints**: if the value is missing (`null` / blank / empty collection), the predicate is a **no-op**.

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

Also: `optionalWhereEqual`, `optionalWhereContains` / `StartsWith` / `EndsWith` / `LikePattern`, `optionalOrWhere` / `optionalOrWhereLike` / `optionalOrWhereIn` / `optionalOrWhereNotEqual` / `optionalOrWhereNotIn`, `optionalWhereNotEqual`, `optionalWhereRelatedEqual`, `optionalWhereRelatedLike`, `optionalWhereNotIn`, `optionalWhereGt` / `Gte` / `Lt` / `Lte` (long aliases `optionalWhereGreaterThan*` / `LessThan*`), `optionalWhereBetween` / `NotBetween`, `optionalWhereDate` / `Year` / `Month` / `Day` / `Time`, `optionalWhereRelation`.

`whereIf` / `when` / `unless` are different: they take an explicit **boolean**, not “value present?”.

### Conditionals (`whereIf`, `when`, `unless`)

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

### Comparisons, ranges, like, in

```java
import java.util.List;

List<Order> orders = orderRepository.query()
        .whereGt("total", minTotal)
        .whereGte("createdAt", from)
        .whereLte("createdAt", to)
        .whereBetween("amount", low, high)      // open ends allowed (only from / only to)
        .whereNotBetween("amount", 0, 10)       // both bounds required
        .whereLike("customerName", term)        // non-null/non-blank; wildcards allowed
        .whereContains("notes", "100%")         // free-text; %/_ escaped
        .whereStartsWith("sku", "AB")
        .whereEndsWith("email", "@acme.com")
        .whereLikePattern("code", "_X%")        // raw pattern
        .whereIn("status", List.of("NEW", "PAID"))
        .whereNotNull("paidAt")
        .get();
```

Aliases: `whereGt` / `whereGte` / `whereLt` / `whereLte` map to the longer `whereGreaterThan*` / `whereLessThan*` methods (same short/long pairing on `optionalWhere*`).

**LIKE rules** (also on `RelatedFilter` / nested `whereHas`):

| API | Behaviour |
|-----|-----------|
| `whereLike` | No `%`/`_` → `%VALUE%`; if wildcards present → raw pattern |
| `whereContains` / `StartsWith` / `EndsWith` | Escaped (`ESCAPE '\'`); prefer for UI free-text |
| `whereLikePattern` | Pattern as-is (trim + upper); you supply wildcards |
| `optionalWhere*` variants | Blank → no-op |

LIKE strategy follows [`spring.fluent-query.like-mode`](#configuration) (default `portable`). Strict LIKE rejects blank strings (`IllegalArgumentException`); use `optionalWhere*` for search params.

### Date and time extracts

Uses Criteria `cb.function` with lowercase names Hibernate maps portably (`year`, `month`, `day`, `hour`, `minute`, `second`) across H2 / PostgreSQL / MySQL / Oracle dialects.

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

Optional variants (`optionalWhereDate` / `Year` / `Month` / `Day` / `Time`) skip when the value is `null`. Same helpers exist on `RelatedFilter`.

### Column-to-column (`whereColumn`)

```java
import java.util.List;

List<Author> authors = authorRepository.query()
        .whereColumn("score", "threshold")           // equal
        .whereColumn("score", ">", "threshold")      // = != <> > >= < <=
        .orWhereColumn("score", "threshold")
        .get();
```

`<>` is normalized to `!=`. Invalid operators throw `IllegalArgumentException`. Metamodel: `whereColumn(SingularAttribute, SingularAttribute)` (+ operator overload).

### Relation filters

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

| Method | Meaning |
|--------|---------|
| `whereHas(relation)` | Association present (collection → `IS NOT EMPTY`; to-one → `IS NOT NULL`) |
| `whereDoesntHave(relation)` | Association absent (collection → `IS EMPTY`; to-one → `IS NULL`) |
| `whereHas(relation, f -> …)` | Nested `EXISTS` with predicates on the related entity (`RelatedFilter`) |
| `whereDoesntHave(relation, f -> …)` | Nested `NOT EXISTS` with the same filter API |
| `orWhereHas` / `orWhereDoesntHave` | OR variants (plain or nested) |
| `whereRelation` / `optionalWhereRelation` | Shortcut for related column equality (`optional*` skips blank/null) |

```java
import java.util.List;

// Authors that have at least one book with more than 100 pages
List<Author> authors = authorRepository.query()
        .whereHas("books", f -> f
                .whereGt("pages", 100)
                .optionalWhereLike("title", titleFragment))
        .orWhereHas("books", f -> f.where("title", "Featured"))
        .get();
```

`RelatedFilter` mirrors the main builder for nested predicates: `where` / `whereEqual` / `whereLike` / `whereContains` / `whereStartsWith` / `whereEndsWith` / `whereLikePattern` / `whereIn` / `whereNotIn` / comparisons (`whereGt`… + long forms) / ranges / date extracts / full `optionalWhere*` family (including escaped LIKE optionals), etc. Nested LIKE also respects `spring.fluent-query.like-mode`.

### Type-safe metamodel

When the host project generates the **JPA static metamodel** (`User_`, `Order_`, …), `FluentQuery` overloads accept `SingularAttribute` / `PluralAttribute` and delegate to the string APIs via `attribute.getName()`.

Coverage includes equality, LIKE family (strict + optional, including Contains/StartsWith/EndsWith/LikePattern), In/NotIn/Null, comparisons, ranges, date extracts, `whereColumn` / `orWhereColumn`, related equal/like (+ optional), relation existence (`whereHas` / …), order, and fetch.

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

Enable metamodel generation in the host (annotation processor / Hibernate JPamodelgen, or your stack’s equivalent). The library itself does not ship generated `*_` classes.

### Fetch, select, distinct, limit, order

| Method | Use for | With `page` / `slice` / `paginate` / `chunk` |
|--------|---------|-----------------------------------------------|
| `fetch("profile")` | To-one (`@ManyToOne` / `@OneToOne`) | ✅ Safe |
| `fetchCollection("orders")` | To-many (`@OneToMany` / `@ManyToMany`) | ❌ Throws `IllegalStateException` |
| `select("id", "email")` | Property projection (`project`); prefer with `*As` | ✅ |
| `distinct()` | Force DISTINCT | ✅ |
| `limit(n)` | Cap rows for `get()` / Spring `limit` | Applied via pageable resolution when useful |
| `orderByAsc` / `orderByDesc` / `orderBy(Sort)` | Sorting | Merged into pageable if unsorted |

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

Page<User> page = userRepository.query()
        .where("active", true)
        .fetch("status")                 // to-one only
        .orderByDesc("createdAt")
        .page(PageRequest.of(0, 20));
```

```java
import org.springframework.data.domain.PageRequest;

// WRONG — cartesian product / bad COUNT
userRepository.query()
        .fetchCollection("orders")
        .page(PageRequest.of(0, 20));   // IllegalStateException
```

Prefer loading collections in a **second query**, or use `get()` / `first()` without pagination when you truly need collection fetch.

### Terminals

| Terminal | Result | Notes |
|----------|--------|-------|
| `first()` | `Optional<T>` | First match (`LIMIT 1`); OK if many match; **no COUNT** |
| `firstOrFail()` | `T` | Throws `FluentQueryNotFoundException` if empty (optional sugar) |
| `firstOrNull()` | `T` or `null` | |
| `latest(property)` | `Optional<T>` | `orderByDesc` + `first` |
| `oldest(property)` | `Optional<T>` | `orderByAsc` + `first` |
| `one()` | `Optional<T>` | Expects 0–1 row; Spring Data throws if **2+** match |
| `oneOrFail()` | `T` | Throws `FluentQueryNotFoundException` if empty (optional sugar) |
| `get()` | `List<T>` | Honors `limit`; without limit can load the whole table |
| `page(pageable)` | `Page<T>` | With COUNT; no `fetchCollection` |
| `paginate(page, size)` | `Page<T>` | 0-based page index + size |
| `slice(pageable)` | `Slice<T>` | **No** COUNT — better for infinite scroll |
| `chunk(size, consumer)` | `void` | Batches via `slice` (no COUNT) |
| `stream()` | `Stream<T>` | **Must** close (`try-with-resources`) |
| `exists()` | `boolean` | Predicates only (no fetch) |
| `count()` | `long` | Predicates only (no fetch) |

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

**Builder contract:** each `query()` builder is **single-use** and **not thread-safe**. Do not share one instance across threads or reuse after a terminal.

### Pagination and chunking

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
        .paginate(0, 20);   // page index, size

userRepository.query()
        .where("active", true)
        .orderByAsc("id")
        .chunk(100, batch -> {
            // process each non-empty batch
        });
```

`chunk` uses `slice` under the hood (no COUNT). Prefer a stable `orderBy*` so batches do not skip or duplicate rows.

### Projections (`as`) and `select`

Uses Spring Data’s `SpecificationFluentQuery.as(Class)` — interface/DTO projections **without** entity join-fetch.

`select("col1", "col2")` is the Eloquent-style alias for Spring Data `project(...)`:

| Combination | What you get |
|-------------|--------------|
| `select(...).getAs(Projection.class)` | **Preferred** — limits projected properties (leaner SQL for interface/DTO projections) |
| `select(...).get()` (entity) | Applies Spring Data `project` / EntityGraph rules; JPA **cannot** return a true “partial entity” like Eloquent |

For a custom column name in the result, expose it on the projection type and keep `select` on the entity attribute path:

```java
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

public interface UserSummary {
    Long getId();
    String getEmail();
    // Custom API name: map from entity property "email" via your projection rules
    // (e.g. getEmailPersonalizado() + @Value / DTO field mapping as needed)
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

Also: `select(User_.id, User_.email)` when the static metamodel is available.

### Typed Specifications

Prefer typed Specs for domain rules; use `where("col", val)` for simple equality:

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

You can also extract the composed Spec without executing:

```java
import org.springframework.data.jpa.domain.Specification;

Specification<User> spec = userRepository.query()
        .where("active", true)
        .whereLike("name", search)
        .toSpecification();
```

## PropertyFilters

`FluentQueryRepository` already extends `PropertyFilters`. When you call `query()`, the builder wires those filters automatically.

Equality, comparisons, null checks, relation helpers, and LIKE go through `PropertyFilters` when present. LIKE follows [`spring.fluent-query.like-mode`](#configuration) (default **`portable`**).

Advanced options:

- Implement only `PropertyFilters` on a custom repository
- Call `FluentQuery.of(executor)` (no rich filters) or `FluentQuery.of(executor, filters)` manually

## Boot 3.x / 4.x compatibility

| Mechanism | Detail |
|-----------|--------|
| Single artifact | One JAR for Boot 3 and 4 |
| Default BOM | Spring Boot **3.5.16** |
| Profile `boot4` | `mvn verify -Pboot4` → Boot **4.1.0** BOM |
| Auto-config order | `afterName` lists Hibernate JPA FQCNs for **both** Boot 3 and Boot 4 packages |
| Spring Data API | Uses `JpaSpecificationExecutor.findBy` + `SpecificationFluentQuery` (Data JPA 3.x+) |

```bash
mvn clean verify          # Boot 3.x BOM
mvn clean verify -Pboot4  # Boot 4.x BOM
```

## Module architecture

```text
spring-fluent-query/
├── spring-fluent-query-core/                 # Publishable. No Boot auto-config.
│   ├── FluentQuery                           # Builder + terminals
│   ├── FluentQueryRepository                 # Repo base + query()
│   ├── PropertyFilters                       # Rich Spec helpers
│   ├── LikeMode / FluentQueryDefaults        # LIKE strategy
│   └── support/                              # Joins, Values, …
│
├── spring-fluent-query-spring-boot-starter/  # Publishable. Auto-config.
│   └── autoconfigure/
│       ├── SpringFluentQueryAutoConfiguration
│       └── SpringFluentQueryProperties       # spring.fluent-query.*
│
└── spring-fluent-query-example/              # Not publishable. Minimal demo.
```

| Maven artifact | When to use it |
|----------------|----------------|
| `spring-fluent-query-spring-boot-starter` | Spring Boot apps (recommended) |
| `spring-fluent-query-core` | Libraries / custom wiring without Boot auto-config |

**Auto-configuration included in the starter:**

| Class | Responsibility |
|-------|----------------|
| `SpringFluentQueryAutoConfiguration` | After Hibernate JPA (Boot 3 + 4); applies `like-mode` to `FluentQueryDefaults` |
| `SpringFluentQueryProperties` | Binds `spring.fluent-query.like-mode` |

## Executable reference (example)

The **`spring-fluent-query-example`** module is a minimal runnable app (H2 + `FluentQueryRepository`).

```bash
docker compose up example   # starts spring-fluent-query-example on :8080
```

Or without Docker:

```bash
mvn -pl spring-fluent-query-example spring-boot:run
```

On startup it runs a full **Create / Read / Update / Delete** sample via `DemoCrudService` (see logs).

The example module also includes H2 `@DataJpaTest` coverage (`FluentQueryDataJpaIT`) for nested `whereHas`, date extracts, `whereColumn`, `whereNotBetween`, `orWhereHas`, `whereRelation`, `unless`, `firstOrFail` / `oneOrFail`, `paginate`, `select` + `firstAs`, optionals, `delete()`, and `fetchCollection` + `page` rejection.

## Troubleshooting

### `query()` is not available on my repository

Extend `FluentQueryRepository<Entity, Id>` (not only `JpaRepository`). One `extends` is enough — do not add a second interface for `PropertyFilters`.

### `fetchCollection` + `page` / `slice` / `paginate` / `chunk` throws

Expected: collection fetch with pagination is unsafe (cartesian product / wrong COUNT). Use `fetch()` for to-one, or load collections in a second query.

### `get()` is slow / OOM

Without `limit`, `get()` can load the whole table. Prefer `page`, `slice`, `paginate`, `chunk`, or `limit`.

### LIKE fails on H2 / PostgreSQL with Oracle syntax

Default mode is **`portable`** (`UPPER` + `LIKE`). If you set `spring.fluent-query.like-mode=oracle-unaccent`, Criteria uses Oracle `CONVERT(..., 'US7ASCII')`, which other databases reject. Use `portable` unless you run on Oracle and need accent folding.

### `whereLike` rejects null/blank

Strict `whereLike` / `whereContains` / `whereStartsWith` / `whereEndsWith` / `whereLikePattern` require a non-null, non-blank value (`NullPointerException` / `IllegalArgumentException`). For search params that may be blank, use the matching `optionalWhere*`.

### LIKE wildcards (`%`, `_`)

- `whereLike("name", "ada")` → `%ADA%` (contains)
- `whereLike("name", "ADA%")` → prefix (raw pattern because `%` is present)
- `whereContains("name", "100%")` → literal `%` in the value (escaped); use this for free-text UI search
- `whereLikePattern("name", "_X%")` → pattern exactly as given (trimmed + upper-cased)

### Custom column names in projections

If you need a personalized name in the API result, define it on the projection interface/DTO and `select` the entity property path (e.g. `select("email").getAs(UserSummary.class)` with `getEmailPersonalizado()` / matching mapping).

### Auto-config does not run on Boot 4

Ensure you use the published starter (imports file under `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) and that `JpaSpecificationExecutor` is on the classpath (`spring-boot-starter-data-jpa`).

## API reference

### Filters

| Method | Description |
|--------|-------------|
| `where(Specification)` | AND typed Spec (`null` Spec ignored) |
| `where(column, value)` | Strict equality; `null` → `IS NULL` |
| `where(Consumer)` | AND group |
| `optionalWhere*` | No-op if value absent: `optionalWhere` / `Equal`, `Like` / `Contains` / `StartsWith` / `EndsWith` / `LikePattern`, `In` / `NotIn`, `NotEqual`, `Gt`/`Gte`/`Lt`/`Lte` (+ long aliases), `Between` / `NotBetween`, date extracts, `Related*` / `Relation`; OR: `optionalOrWhere` / `Like` / `In` / `NotEqual` / `NotIn` |
| `whereIf` / `when` / `unless` | Boolean conditionals; `when` supports then/else (Eloquent); `unless` = `when(!condition, …)` |
| `orWhere(...)` | OR Spec / equality / group |
| `whereNot` | NOT Spec |
| `whereEqual` / `whereEqualIf` / `whereNotEqual` | Explicit equality / inequality |
| `whereRelatedEqual` / `whereRelatedLike` | Join + filter (`toPattern` rules for Like) |
| `whereLike` | Case-insensitive LIKE; no `%`/`_` → contains (`%VALUE%`); if value has `%`/`_` → raw pattern; rejects blank |
| `whereContains` / `whereStartsWith` / `whereEndsWith` | Escaped free-text LIKE (`%`/`_` literal); prefer for user search |
| `whereLikePattern` | Raw pattern as-is (you supply wildcards) |
| `whereIn` / `whereNotIn` | Membership; empty → disjunction / conjunction |
| `whereNull` / `whereNotNull` | Null checks |
| `whereGt` / `whereGte` / `whereLt` / `whereLte` | Comparisons (long aliases `whereGreaterThan*` / `whereLessThan*`) |
| `whereBetween` / `whereNotBetween` | Inclusive range / negated range |
| `whereDate` / `whereYear` / `whereMonth` / `whereDay` / `whereTime` | Temporal extracts (`cb.function`) |
| `whereColumn` / `orWhereColumn` | Column-to-column comparison |
| `whereHas` / `whereDoesntHave` / `orWhereHas` / `orWhereDoesntHave` | Association exists / absent (optional nested `EXISTS`) |
| `whereRelation` / `optionalWhereRelation` | Related column equality shortcut |
| Metamodel overloads | String APIs above with `SingularAttribute` / `PluralAttribute` (filters, related, has/doesntHave, `select`, order, fetch) |
| `of(executor)` / `of(executor, filters)` / `query()` | Factory / repository entry points |

### Load / shape

| Method | Description |
|--------|-------------|
| `fetch` | LEFT JOIN FETCH to-one; enables DISTINCT |
| `fetchCollection` | LEFT JOIN FETCH to-many; **not** with page/slice/paginate/chunk |
| `select` | Eloquent-style property projection (`project`); best with `*As` |
| `distinct` | Force DISTINCT |
| `limit` | Max rows for list terminals |
| `orderByAsc` / `orderByDesc` / `orderBy` | Sorting |

### Terminals

| Method | Description |
|--------|-------------|
| `first` / `firstOrNull` / `firstOrFail` | First row (`LIMIT 1`); OK if many match; `*OrFail` throws if empty |
| `latest` / `latestOrNull` | `orderByDesc` + first |
| `oldest` / `oldestOrNull` | `orderByAsc` + first |
| `one` / `oneOrNull` / `oneOrFail` | Exactly 0–1 row; **throws if 2+** (`IncorrectResultSizeDataAccessException`); `*OrFail` throws if empty |
| `get` | List (honors `limit`) |
| `page` | Page with COUNT |
| `paginate` | Page by 0-based index + size |
| `slice` | Slice without COUNT |
| `chunk` | Batch via slice |
| `stream` | Closeable stream |
| `firstAs` / `getAs` / `pageAs` | Projections |
| `exists` / `count` | Aggregate without fetch |
| `delete` | Delete matching rows (`CrudRepository#deleteAll`) |
| `toSpecification` / `toSelectSpecification` / `toSort` | Inspect composition |

## Development

```text
spring-fluent-query/
├── spring-fluent-query-core/
├── spring-fluent-query-spring-boot-starter/
└── spring-fluent-query-example/    ← minimal demo
```

```bash
# Build and run all tests (Boot 3 BOM)
docker compose run --rm maven

# Boot 4 BOM
docker compose run --rm maven mvn clean verify -Pboot4

# Core tests only
docker compose run --rm maven mvn -pl spring-fluent-query-core test

# Example app
docker compose up example

# Install to local .m2
docker compose run --rm maven mvn clean install
```

Without Docker:

```bash
mvn clean verify
mvn clean verify -Pboot4
mvn -pl spring-fluent-query-example spring-boot:run
```

Releases are published to Maven Central — see [PUBLISHING.md](PUBLISHING.md) (maintainers).

## Roadmap

- Richer example module (REST + sample queries)
- `autoPublish=true` in Central Portal when release automation is stable

## License

Copyright © 2026 **Benjamín Olvera R.**

Licensed under the [Apache License, Version 2.0](LICENSE).
