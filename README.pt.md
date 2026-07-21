# Spring Fluent Query

[🇬🇧 English version](README.md) | [🇪🇸 Versión en español](README.es.md)

[![Java](https://img.shields.io/badge/Java-17+-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%7C%204.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-3.x%20%7C%204.x-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-data-jpa)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter?label=Maven%20Central)](https://search.maven.org/artifact/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Consultas fluidas **estilo Eloquent** sobre **Spring Data JPA Specifications**, sem Active Record.

Spring Fluent Query adiciona uma cadeia legível (`where` → `fetch` → `latest` / `first` / `page`) enquanto a **execução** delega à API fluida oficial do Spring Data `findBy(spec, q → …)`. Assim, `first()` / `latest()` usam `LIMIT 1` **sem** um COUNT desnecessário.

> Compatível com **Spring Boot 3.x e 4.x** (mesmo JAR do starter). Build padrão: BOM Boot **3.5.x**; CI também verifica `-Pboot4` (Boot **4.1.x**).

**Inclui:**

- Builder fluido sobre `JpaSpecificationExecutor` (`FluentQuery`)
- Uma única base de repositório: `FluentQueryRepository` (CRUD + Specs + `PropertyFilters` + `query()`)
- Filtros estritos (`where*` / `orWhere*`) e de busca (`optionalWhere*`)
- Condicionais booleanos: `whereIf` / `when` / `unless`
- Grupos: `where(q → …)` / `orWhere(q → …)`
- Extratos de data/hora: `whereDate` / `whereYear` / `whereMonth` / `whereDay` / `whereTime` (+ `optional*`)
- Coluna a coluna: `whereColumn` / `orWhereColumn`; intervalos: `whereBetween` / `whereNotBetween`
- Existência de relação: `whereHas` / `whereDoesntHave` / `orWhereHas` / `orWhereDoesntHave` / `whereRelation` (`EXISTS` aninhado opcional via `RelatedFilter`)
- Sobrecargas type-safe do metamodelo (`User_.email`, …) quando o host gera o metamodelo estático JPA
- Carga eager: `fetch` (to-one) e `fetchCollection` (to-many, com regras claras de paginação)
- Projeção de colunas: `select(...)` (Spring Data `project`; preferível com `*As`)
- Paginação: `page` / `slice` / `paginate` / `chunk`
- Terminais: `first` / `firstOrFail` / `latest` / `oldest` / `one` / `oneOrFail` / `get` / `stream` / `exists` / `count` (+ `*OrNull` / `*As`)
- LIKE portátil por padrão (`UPPER` + `LIKE`); modo Oracle unaccent opcional
- Core utilizável sem o starter do Boot (`spring-fluent-query-core`)

<a id="por-que-usar-fluent-query"></a>
## Por que usar Fluent Query?

O Spring Data já tem Specifications e `JpaSpecificationExecutor.findBy`, mas compor muitos filtros **opcionais** continua verboso, e padrões comuns (última linha, fetch to-one + page) são fáceis de errar ou caros.

**Fluent Query é uma camada de DX** sobre o mesmo motor: não substitui o Spring Data JPA — compõe predicados e delega os terminais à API fluida oficial.

| Sem Fluent Query | Com Fluent Query |
|------------------|------------------|
| `Specification.where(a).and(b)` manual e null checks | `query().where("a", x).where("b", y)` — **estrito** (sempre aplicado) |
| Params de busca opcionais → `if (value != null)` na mão | `optionalWhere` / `optionalWhereLike` / `optionalWhereIn` … |
| `findAll(spec, PageRequest.of(0,1))` (muitas vezes COUNT + SELECT) | `latest("createdAt")` → LIMIT 1, **sem COUNT** |
| N+1 em associações | `fetch("status")` para to-one |
| Bugs de paginação + fetch de coleções | `fetchCollection` bloqueado com `page` / `slice` |
| Condicionais booleanos espalhados no serviço | `whereIf` / `when` |
| Batches na mão com slices | `chunk(size, batch → …)` |

**Quando Specs simples bastam:** um ou dois predicados fixos, sem filtros opcionais, sem necessidade de encadeamento estilo Eloquent.

**Quando Fluent Query compensa:** endpoints de busca com muitos filtros opcionais, “último por data”, fetch to-one com paginação, filtros por relação, processamento em chunks, ou times vindos do Laravel Eloquent.

Você ainda pode misturar `Specification` tipados com helpers de coluna string na mesma cadeia.

## Sumário

- [Por que usar Fluent Query?](#por-que-usar-fluent-query)
- [Requisitos](#requisitos)
- [Início rápido](#inicio-rapido)
- [Padrão CRUD](#padrao-crud)
- [Configuração](#configuracao)
- [Guia de uso](#guia-de-uso)
  - [Imports de referência](#imports-de-referencia)
  - [Filtros estritos (`where*`, `orWhere*`, grupos)](#filtros-estritos-where-orwhere-grupos)
  - [Filtros opcionais (`optionalWhere*`)](#filtros-opcionais-optionalwhere)
  - [Condicionais (`whereIf`, `when`, `unless`)](#condicionais-whereif-when-unless)
  - [Comparações, intervalos, like, in](#comparacoes-intervalos-like-in)
  - [Extratos de data e hora](#extratos-de-data-e-hora)
  - [Coluna a coluna (`whereColumn`)](#coluna-a-coluna-wherecolumn)
  - [Filtros por relação](#filtros-por-relacao)
  - [Metamodelo type-safe](#metamodelo-type-safe)
  - [Fetch, select, distinct, limit, order](#fetch-select-distinct-limit-order)
  - [Terminais](#terminais)
  - [Paginação e chunking](#paginacao-e-chunking)
  - [Projeções (`as`) e `select`](#projecoes-as-e-select)
  - [Specifications tipadas](#specifications-tipadas)
- [PropertyFilters](#propertyfilters)
- [Compatibilidade Boot 3.x / 4.x](#compatibilidade-boot-3-4)
- [Arquitetura de módulos](#arquitetura-de-modulos)
- [Referência executável (example)](#referencia-executavel-example)
- [Solução de problemas](#solucao-de-problemas)
- [Referência de API](#referencia-de-api)
- [Desenvolvimento](#desenvolvimento)
- [Roadmap](#roadmap)
- [Licença](#licenca)

<a id="requisitos"></a>
## Requisitos

- **Java 17+**
- **Spring Boot 3.x ou 4.x** (mesmo JAR do starter)
- **Spring Data JPA** na **sua** app (`spring-boot-starter-data-jpa` — ver abaixo)

| Spring Boot | BOM padrão neste repo | Fluent Query |
|-------------|-----------------------|--------------|
| 3.x | 3.5.16 | Suportado (CI padrão) |
| 4.x | 4.1.0 via `-Pboot4` | Suportado (`mvn verify -Pboot4`) |

O mesmo JAR do starter funciona em ambos. A ordem de auto-config usa `afterName` com FQCN do Hibernate JPA para pacotes de **Boot 3 e Boot 4**.

<a id="quais-dependencias-eu-instalo"></a>
### Quais dependências eu instalo?

| Dependência | Você adiciona? | Quando |
|-------------|----------------|--------|
| `spring-fluent-query-spring-boot-starter` | **Sim** | Sempre (FluentQuery + base de repo + auto-config) |
| `spring-boot-starter-data-jpa` | **Sim** | Obrigatório — JPA é **opcional** no starter para você controlar o stack |
| Driver JDBC + DataSource | Sim | Seu banco (H2 em tests; PostgreSQL, MySQL, Oracle, … em prod) |

**Não instale separadamente** (já vêm de forma transitiva quando há JPA):

| Dependência | Motivo |
|-------------|--------|
| `spring-fluent-query-core` | Incluído pelo starter |
| `spring-data-jpa` | Vem com `spring-boot-starter-data-jpa` |

<a id="sem-spring-boot"></a>
### Sem Spring Boot

Use `spring-fluent-query-core` e conecte `FluentQuery.of(executor)` (ou `FluentQuery.of(executor, filters)`) você mesmo. Detalhes em [Arquitetura de módulos](#arquitetura-de-modulos).

<a id="inicio-rapido"></a>
## Início rápido

### 1. Dependências

Adicione o starter do Fluent Query **e** Spring Data JPA:

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

**Maven multi-módulo** (mesmo repositório):

```xml
<dependency>
    <groupId>io.github.benjaminor-dev</groupId>
    <artifactId>spring-fluent-query-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

> Disponível no [Maven Central](https://search.maven.org/artifact/io.github.benjaminor-dev/spring-fluent-query-spring-boot-starter) — não é preciso configurar repositórios extras.

### 2. Estenda uma única interface de repositório

```java
import dev.benjaminor.fluentquery.FluentQueryRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends FluentQueryRepository<User, Long> {
}
```

Esse único `extends` dá a você `JpaRepository`, `JpaSpecificationExecutor`, `PropertyFilters` e `query()`.

### 3. Consulte

```java
import java.util.Optional;

Optional<User> user = userRepository.query()
        .where("email", email)
        .fetch("profile")
        .latest("createdAt");
```

> Todos os blocos Java deste README incluem **imports completos** para copiar e colar sem adivinhar a origem.


<a id="padrao-crud"></a>
## Padrão CRUD

`FluentQueryRepository` continua sendo um `JpaRepository` do Spring Data: **escritas** usam `save` / `delete`, **leituras** (e delete filtrado) usam `query()`.

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
     * Apagar <b>uma</b> linha pela primary key.
     * Carrega com {@code first()} (ou {@code one()}) e depois {@code delete(entity)} —
     * não use o {@code delete()} em massa para uma única linha conhecida.
     *
     * @return {@code true} se uma linha foi apagada
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
        // também válido: userRepository.findById(id).ifPresent(userRepository::delete);
    }

    /**
     * Apagar <b>muitas</b> linhas que satisfazem um filtro.
     * {@code delete()} remove <em>todas</em> as coincidências — mantenha o {@code where*}
     * restrito (e considere {@code count()} / dry-run antes em ferramentas de admin).
     *
     * @return número de linhas apagadas
     */
    @Transactional
    public long deleteAllInactive() {
        return userRepository.query()
                .where("status", "INACTIVE")
                .delete();
    }
}
```

| Operação | Chamada típica |
|----------|----------------|
| **Create** | `repository.save(entity)` |
| **Read** | `repository.query().where(...).first()` / `get()` / `page(...)` |
| **Update** | `query().where("id", id).first()` → mutar → `save` |
| **Delete uma** | `query().where("id", id).first()` → `repository.delete(entity)` (ou `findById` / `deleteById`) |
| **Delete muitas** | `query().where(...).delete()` — apaga **todas** as coincidências; restrinja bem o filtro |

O módulo example percorre este fluxo na inicialização ([`DemoCrudService`](spring-fluent-query-example/src/main/java/dev/benjaminor/fluentquery/example/DemoCrudService.java)).

<a id="configuracao"></a>
## Configuração

O starter registra `SpringFluentQueryAutoConfiguration` depois do Hibernate JPA (FQCN de Boot 3 e Boot 4) e aplica `spring.fluent-query.*` a `FluentQueryDefaults`.

<a id="modo-like"></a>
### Modo LIKE

Propriedade: `spring.fluent-query.like-mode` (enum [`LikeMode`](spring-fluent-query-core/src/main/java/dev/benjaminor/fluentquery/LikeMode.java)).

**Não há um switch por banco** (H2 / PostgreSQL / MySQL / SQL Server) — só estes dois valores:

| Valor (YAML / properties) | Enum | Forma SQL | Quando usar |
|---------------------------|------|-----------|-------------|
| `portable` (**default**) | `LikeMode.PORTABLE` | `UPPER(column) LIKE %VALUE%` | Qualquer BD suportada pelo Hibernate (H2, PostgreSQL, MySQL, SQL Server, Oracle, …) |
| `oracle-unaccent` | `LikeMode.ORACLE_UNACCENT` | `UPPER(CONVERT(column, 'US7ASCII')) LIKE %VALUE%` | Só Oracle, quando precisa de dobramento de acentos |

**YAML**

```yaml
spring:
  fluent-query:
    like-mode: portable          # default — pode omitir
    # like-mode: oracle-unaccent # só Oracle (acentos)
```

**Properties**

```properties
spring.fluent-query.like-mode=portable
# spring.fluent-query.like-mode=oracle-unaccent
```

`oracle-unaccent` **não** é portátil: H2 / PostgreSQL / MySQL rejeitam o `CONVERT(..., 'US7ASCII')` do Oracle. Fique em `portable` salvo se rodar no Oracle e precisar de matching sem acentos.

Hoje não existem outras propriedades `spring.fluent-query.*`: estenda `FluentQueryRepository` e chame `query()`.

<a id="guia-de-uso"></a>
## Guia de uso

<a id="imports-de-referencia"></a>
### Imports de referência

| Origem | Import típico | Quando |
|--------|---------------|--------|
| Fluent Query | `import dev.benjaminor.fluentquery.FluentQueryRepository;` | Base do repositório |
| Fluent Query | `import dev.benjaminor.fluentquery.FluentQuery;` | Raro — prefira `repository.query()` |
| Spring Data | `import org.springframework.data.domain.*;` | `Pageable`, `Page`, `Slice`, `Sort` |
| Spring Data JPA | `import org.springframework.data.jpa.domain.Specification;` | Scopes tipados |

<a id="filtros-estritos-where-orwhere-grupos"></a>
### Filtros estritos (`where*`, `orWhere*`, grupos)

`where*` / `orWhere*` são **estritos**: o predicado **sempre** é aplicado.

| Chamada | Comportamento |
|---------|---------------|
| `where(col, null)` / `whereEqual(null)` | `IS NULL` |
| `whereNotEqual(null)` | `IS NOT NULL` |
| `whereIn([])` | Disjunção (não casa nada) |
| `whereNotIn([])` | Conjunção (sempre verdadeiro) |
| `whereLike(col, null)` | `NullPointerException` |

Strings são aparadas (`trim`) antes da comparação; blank **não** é omitido (use `optionalWhere*` para isso).

```java
import java.util.List;

List<User> users = userRepository.query()
        .where("status", "ACTIVE")
        .where("deletedAt", null)   // IS NULL
        .orWhere("role", "ADMIN")
        .get();
```

**Grupo AND** — `where(q → …)` aninha predicados com AND e depois faz AND do grupo na query externa:

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

<a id="filtros-opcionais-optionalwhere"></a>
### Filtros opcionais (`optionalWhere*`)

Use-os em **endpoints de busca**: se o valor faltar (`null` / blank / coleção vazia), o predicado é um **no-op**.

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

Também: `optionalWhereEqual`, `optionalWhereContains` / `StartsWith` / `EndsWith` / `LikePattern`, `optionalOrWhere` / `optionalOrWhereLike` / `optionalOrWhereIn` / `optionalOrWhereNotEqual` / `optionalOrWhereNotIn`, `optionalWhereNotEqual`, `optionalWhereRelatedEqual`, `optionalWhereRelatedLike`, `optionalWhereNotIn`, `optionalWhereGt` / `Gte` / `Lt` / `Lte` (aliases longos `optionalWhereGreaterThan*` / `LessThan*`), `optionalWhereBetween` / `NotBetween`, `optionalWhereDate` / `Year` / `Month` / `Day` / `Time`, `optionalWhereRelation`.

`whereIf` / `when` / `unless` são diferentes: recebem um **booleano** explícito, não “há valor?”.

<a id="condicionais-whereif-when-unless"></a>
### Condicionais (`whereIf`, `when`, `unless`)

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

<a id="comparacoes-intervalos-like-in"></a>
### Comparações, intervalos, like, in

```java
import java.util.List;

List<Order> orders = orderRepository.query()
        .whereGt("total", minTotal)
        .whereGte("createdAt", from)
        .whereLte("createdAt", to)
        .whereBetween("amount", low, high)      // extremos abertos permitidos (só from / só to)
        .whereNotBetween("amount", 0, 10)       // ambos os extremos obrigatórios
        .whereLike("customerName", term)        // non-null/non-blank; wildcards permitidos
        .whereContains("notes", "100%")         // texto livre; %/_ escapados
        .whereStartsWith("sku", "AB")
        .whereEndsWith("email", "@acme.com")
        .whereLikePattern("code", "_X%")        // padrão raw
        .whereIn("status", List.of("NEW", "PAID"))
        .whereNotNull("paidAt")
        .get();
```

Aliases: `whereGt` / `whereGte` / `whereLt` / `whereLte` mapeiam para os métodos longos `whereGreaterThan*` / `whereLessThan*` (mesmo emparelhamento curto/longo em `optionalWhere*`).

**Regras LIKE** (também em `RelatedFilter` / `whereHas` aninhado):

| API | Comportamento |
|-----|---------------|
| `whereLike` | Sem `%`/`_` → `%VALUE%`; se há wildcards → padrão raw |
| `whereContains` / `StartsWith` / `EndsWith` | Escapado (`ESCAPE '\'`); preferível para texto livre de UI |
| `whereLikePattern` | Padrão tal qual (trim + upper); você fornece os wildcards |
| Variantes `optionalWhere*` | Blank → no-op |

A estratégia LIKE segue [`spring.fluent-query.like-mode`](#configuracao) (default `portable`). O LIKE estrito rejeita strings em branco (`IllegalArgumentException`); use `optionalWhere*` para params de busca.

<a id="extratos-de-data-e-hora"></a>
### Extratos de data e hora

Usa Criteria `cb.function` com nomes em minúsculas que o Hibernate mapeia de forma portátil (`year`, `month`, `day`, `hour`, `minute`, `second`) nos dialetos H2 / PostgreSQL / MySQL / Oracle.

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

Variantes opcionais (`optionalWhereDate` / `Year` / `Month` / `Day` / `Time`) omitem o predicado se o valor for `null`. Os mesmos helpers existem em `RelatedFilter`.

<a id="coluna-a-coluna-wherecolumn"></a>
### Coluna a coluna (`whereColumn`)

```java
import java.util.List;

List<Author> authors = authorRepository.query()
        .whereColumn("score", "threshold")           // igualdade
        .whereColumn("score", ">", "threshold")      // = != <> > >= < <=
        .orWhereColumn("score", "threshold")
        .get();
```

`<>` é normalizado para `!=`. Operadores inválidos lançam `IllegalArgumentException`. Metamodelo: `whereColumn(SingularAttribute, SingularAttribute)` (+ overload com operador).

<a id="filtros-por-relacao"></a>
### Filtros por relação

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
| `whereHas(relation)` | Associação presente (coleção → `IS NOT EMPTY`; to-one → `IS NOT NULL`) |
| `whereDoesntHave(relation)` | Associação ausente (coleção → `IS EMPTY`; to-one → `IS NULL`) |
| `whereHas(relation, f -> …)` | `EXISTS` aninhado com predicados na entidade relacionada (`RelatedFilter`) |
| `whereDoesntHave(relation, f -> …)` | `NOT EXISTS` aninhado com a mesma API de filtro |
| `orWhereHas` / `orWhereDoesntHave` | Variantes OR (simples ou aninhada) |
| `whereRelation` / `optionalWhereRelation` | Atalho de igualdade em coluna relacionada (`optional*` omite blank/null) |

```java
import java.util.List;

// Autores com pelo menos um livro com mais de 100 páginas
List<Author> authors = authorRepository.query()
        .whereHas("books", f -> f
                .whereGt("pages", 100)
                .optionalWhereLike("title", titleFragment))
        .orWhereHas("books", f -> f.where("title", "Featured"))
        .get();
```

`RelatedFilter` espelha o builder principal para predicados aninhados: `where` / `whereEqual` / `whereLike` / `whereContains` / `whereStartsWith` / `whereEndsWith` / `whereLikePattern` / `whereIn` / `whereNotIn` / comparações (`whereGt`… + formas longas) / intervalos / extratos de data / família completa `optionalWhere*` (inclui opcionais LIKE escapados), etc. O LIKE aninhado também respeita `spring.fluent-query.like-mode`.

<a id="metamodelo-type-safe"></a>
### Metamodelo type-safe

Quando o projeto host gera o **metamodelo estático JPA** (`User_`, `Order_`, …), o `FluentQuery` oferece sobrecargas com `SingularAttribute` / `PluralAttribute` que delegam às APIs string via `attribute.getName()`.

A cobertura inclui igualdade, família LIKE (estrita + opcional, incluindo Contains/StartsWith/EndsWith/LikePattern), In/NotIn/Null, comparações, intervalos, extratos de data, `whereColumn` / `orWhereColumn`, related equal/like (+ opcional), existência de relação (`whereHas` / …), order e fetch.

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

Ative a geração do metamodelo no host (annotation processor / Hibernate JPamodelgen, ou o equivalente da sua stack). A biblioteca não publica classes `*_` geradas.

<a id="fetch-select-distinct-limit-order"></a>
### Fetch, select, distinct, limit, order

| Método | Uso | Com `page` / `slice` / `paginate` / `chunk` |
|--------|-----|---------------------------------------------|
| `fetch("profile")` | To-one (`@ManyToOne` / `@OneToOne`) | ✅ Seguro |
| `fetchCollection("orders")` | To-many (`@OneToMany` / `@ManyToMany`) | ❌ Lança `IllegalStateException` |
| `select("id", "email")` | Projeção de propriedades (`project`); preferível com `*As` | ✅ |
| `distinct()` | Forçar DISTINCT | ✅ |
| `limit(n)` | Teto de linhas em `get()` / `limit` do Spring | Aplicado via pageable quando útil |
| `orderByAsc` / `orderByDesc` / `orderBy(Sort)` | Ordenação | Mesclado no pageable se não houver sort |

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

Page<User> page = userRepository.query()
        .where("active", true)
        .fetch("status")                 // só to-one
        .orderByDesc("createdAt")
        .page(PageRequest.of(0, 20));
```

```java
import org.springframework.data.domain.PageRequest;

// ERRADO — produto cartesiano / COUNT incorreto
userRepository.query()
        .fetchCollection("orders")
        .page(PageRequest.of(0, 20));   // IllegalStateException
```

Prefira carregar coleções em uma **segunda query**, ou use `get()` / `first()` sem paginação quando realmente precisar de fetch de coleção.

<a id="terminais"></a>
### Terminais

| Terminal | Resultado | Notas |
|----------|-----------|-------|
| `first()` | `Optional<T>` | Primeiro match (`LIMIT 1`); OK se houver vários; **sem COUNT** |
| `firstOrFail()` | `T` | Lança `FluentQueryNotFoundException` se vazio (açúcar opcional) |
| `firstOrNull()` | `T` ou `null` | |
| `latest(property)` | `Optional<T>` | `orderByDesc` + `first` |
| `oldest(property)` | `Optional<T>` | `orderByAsc` + `first` |
| `one()` | `Optional<T>` | Espera 0–1 linha; Spring Data lança se houver **2+** |
| `oneOrFail()` | `T` | Lança `FluentQueryNotFoundException` se vazio (açúcar opcional) |
| `get()` | `List<T>` | Respeita `limit`; sem limit pode carregar a tabela inteira |
| `page(pageable)` | `Page<T>` | Com COUNT; sem `fetchCollection` |
| `paginate(page, size)` | `Page<T>` | Índice 0-based + size |
| `slice(pageable)` | `Slice<T>` | **Sem** COUNT — melhor para scroll infinito |
| `chunk(size, consumer)` | `void` | Batches via `slice` (sem COUNT) |
| `stream()` | `Stream<T>` | **Deve** ser fechado (`try-with-resources`) |
| `exists()` | `boolean` | Só predicados (sem fetch) |
| `count()` | `long` | Só predicados (sem fetch) |

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

**Contrato do builder:** cada builder de `query()` é de **uso único** e **não é thread-safe**. Não compartilhe uma instância entre threads nem a reutilize após um terminal.

<a id="paginacao-e-chunking"></a>
### Paginação e chunking

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
            // processar cada batch não vazio
        });
```

`chunk` usa `slice` por baixo (sem COUNT). Prefira um `orderBy*` estável para não pular nem duplicar linhas.

<a id="projecoes-as-e-select"></a>
### Projeções (`as`) e `select`

Usa `SpecificationFluentQuery.as(Class)` do Spring Data — projeções interface/DTO **sem** join-fetch de entidade.

`select("col1", "col2")` é o alias estilo Eloquent do Spring Data `project(...)`:

| Combinação | O que você obtém |
|------------|------------------|
| `select(...).getAs(Projection.class)` | **Preferido** — limita propriedades projetadas (SQL mais leve em interface/DTO) |
| `select(...).get()` (entidade) | Aplica regras `project` / EntityGraph do Spring Data; JPA **não** pode devolver uma “entidade parcial” como o Eloquent |

Se quiser um nome personalizado no resultado, exponha-o no tipo de projeção e deixe o `select` com o caminho do atributo da entidade:

```java
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

public interface UserSummary {
    Long getId();
    String getEmail();
    // Nome de API personalizado: mapeie a partir da propriedade da entidade "email"
    // (p. ex. getEmailPersonalizado() + @Value / mapeamento DTO conforme precisar)
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

Também: `select(User_.id, User_.email)` se o metamodelo estático estiver disponível.

<a id="specifications-tipadas"></a>
### Specifications tipadas

Prefira Specs tipados para regras de domínio; use `where("col", val)` para igualdade simples:

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

Você também pode extrair o Spec composto sem executar:

```java
import org.springframework.data.jpa.domain.Specification;

Specification<User> spec = userRepository.query()
        .where("active", true)
        .whereLike("name", search)
        .toSpecification();
```

<a id="propertyfilters"></a>
## PropertyFilters

`FluentQueryRepository` já estende `PropertyFilters`. Ao chamar `query()`, o builder os conecta automaticamente.

Igualdade, comparações, null checks, helpers de relação e LIKE passam por `PropertyFilters` quando presentes. LIKE segue [`spring.fluent-query.like-mode`](#configuracao) (default **`portable`**).

Opções avançadas:

- Implementar só `PropertyFilters` em um repositório custom
- Chamar `FluentQuery.of(executor)` (sem filtros ricos) ou `FluentQuery.of(executor, filters)` manualmente

<a id="compatibilidade-boot-3-4"></a>
## Compatibilidade Boot 3.x / 4.x

| Mecanismo | Detalhe |
|-----------|---------|
| Artefato único | Um JAR para Boot 3 e 4 |
| BOM padrão | Spring Boot **3.5.16** |
| Perfil `boot4` | `mvn verify -Pboot4` → BOM Boot **4.1.0** |
| Ordem de auto-config | `afterName` lista FQCN do Hibernate JPA para **ambos** pacotes Boot 3 e Boot 4 |
| API Spring Data | Usa `JpaSpecificationExecutor.findBy` + `SpecificationFluentQuery` (Data JPA 3.x+) |

```bash
mvn clean verify          # BOM Boot 3.x
mvn clean verify -Pboot4  # BOM Boot 4.x
```

<a id="arquitetura-de-modulos"></a>
## Arquitetura de módulos

```text
spring-fluent-query/
├── spring-fluent-query-core/                 # Publicável. Sem auto-config do Boot.
│   ├── FluentQuery                           # Builder + terminais
│   ├── FluentQueryRepository                 # Base de repo + query()
│   ├── PropertyFilters                       # Helpers ricos de Spec
│   ├── LikeMode / FluentQueryDefaults        # Estratégia LIKE
│   └── support/                              # Joins, Values, …
│
├── spring-fluent-query-spring-boot-starter/  # Publicável. Auto-config.
│   └── autoconfigure/
│       ├── SpringFluentQueryAutoConfiguration
│       └── SpringFluentQueryProperties       # spring.fluent-query.*
│
└── spring-fluent-query-example/              # Não publicável. Demo mínima.
```

Fontes-chave: [`FluentQuery`](spring-fluent-query-core/src/main/java/dev/benjaminor/fluentquery/FluentQuery.java) · [`FluentQueryRepository`](spring-fluent-query-core/src/main/java/dev/benjaminor/fluentquery/FluentQueryRepository.java) · [`PropertyFilters`](spring-fluent-query-core/src/main/java/dev/benjaminor/fluentquery/PropertyFilters.java) · [`RelatedFilter`](spring-fluent-query-core/src/main/java/dev/benjaminor/fluentquery/RelatedFilter.java)

| Artefato Maven | Quando usar |
|----------------|-------------|
| [`spring-fluent-query-spring-boot-starter`](spring-fluent-query-spring-boot-starter/) | Apps Spring Boot (recomendado) |
| [`spring-fluent-query-core`](spring-fluent-query-core/) | Bibliotecas / wiring custom sem auto-config do Boot |

**Auto-configuração incluída no starter:**

| Classe | Responsabilidade |
|--------|------------------|
| [`SpringFluentQueryAutoConfiguration`](spring-fluent-query-spring-boot-starter/src/main/java/dev/benjaminor/fluentquery/autoconfigure/SpringFluentQueryAutoConfiguration.java) | Após Hibernate JPA (Boot 3 + 4); aplica `like-mode` a `FluentQueryDefaults` |
| [`SpringFluentQueryProperties`](spring-fluent-query-spring-boot-starter/src/main/java/dev/benjaminor/fluentquery/autoconfigure/SpringFluentQueryProperties.java) | Bind de `spring.fluent-query.like-mode` |

<a id="referencia-executavel-example"></a>
## Referência executável (example)

O módulo **`spring-fluent-query-example`** é uma app mínima executável (H2 + `FluentQueryRepository`).

```bash
docker compose up example   # sobe spring-fluent-query-example em :8080
```

Ou sem Docker:

```bash
mvn -pl spring-fluent-query-example spring-boot:run
```

Na inicialização executa um fluxo completo **Create / Read / Update / Delete** com [`DemoCrudService`](spring-fluent-query-example/src/main/java/dev/benjaminor/fluentquery/example/DemoCrudService.java) (ver logs).

O módulo example também inclui cobertura H2 `@DataJpaTest` ([`FluentQueryDataJpaIT`](spring-fluent-query-example/src/test/java/dev/benjaminor/fluentquery/example/it/FluentQueryDataJpaIT.java)) para `whereHas` aninhado, extratos de data, `whereColumn`, `whereNotBetween`, `orWhereHas`, `whereRelation`, `unless`, `firstOrFail` / `oneOrFail`, `paginate`, `select` + `firstAs`, optionals, `delete()` e rejeição de `fetchCollection` + `page`.

<a id="solucao-de-problemas"></a>
## Solução de problemas

### `query()` não está disponível no meu repositório

Estenda `FluentQueryRepository<Entity, Id>` (não só `JpaRepository`). Um `extends` basta — não é preciso uma segunda interface para `PropertyFilters`.

### `fetchCollection` + `page` / `slice` / `paginate` / `chunk` lança

Esperado: fetch de coleção com paginação é inseguro (produto cartesiano / COUNT incorreto). Use `fetch()` para to-one, ou carregue coleções em uma segunda query.

### `get()` é lento / OOM

Sem `limit`, `get()` pode carregar a tabela inteira. Prefira `page`, `slice`, `paginate`, `chunk` ou `limit`.

### LIKE falha em H2 / PostgreSQL com sintaxe Oracle

O modo padrão é **`portable`** (`UPPER` + `LIKE`). Se você definir `spring.fluent-query.like-mode=oracle-unaccent`, o Criteria usa `CONVERT(..., 'US7ASCII')` do Oracle, que outros bancos rejeitam. Use `portable` salvo se rodar no Oracle e precisar de dobramento de acentos.

### `whereLike` rejeita null/blank

Os `whereLike` / `whereContains` / `whereStartsWith` / `whereEndsWith` / `whereLikePattern` estritos exigem um valor non-null e non-blank (`NullPointerException` / `IllegalArgumentException`). Para params de busca que podem vir vazios, use o `optionalWhere*` correspondente.

### Wildcards LIKE (`%`, `_`)

- `whereLike("name", "ada")` → `%ADA%` (contains)
- `whereLike("name", "ADA%")` → prefixo (padrão raw porque há `%`)
- `whereContains("name", "100%")` → `%` literal no valor (escapado); preferível para busca livre de UI
- `whereLikePattern("name", "_X%")` → padrão exatamente como dado (trim + upper)

### Nomes personalizados em projeções

Se precisar de um nome personalizado no resultado da API, defina-o na interface/DTO de projeção e faça `select` do caminho do atributo da entidade (p. ex. `select("email").getAs(UserSummary.class)` com `getEmailPersonalizado()` / o mapeamento correspondente).

### A auto-config não roda no Boot 4

Garanta que você usa o starter publicado (arquivo de imports em `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) e que `JpaSpecificationExecutor` está no classpath (`spring-boot-starter-data-jpa`).

<a id="referencia-de-api"></a>
## Referência de API

<a id="filtros"></a>
### Filtros

| Método | Descrição |
|--------|-----------|
| `where(Specification)` | AND de Spec tipado (`null` Spec é ignorado) |
| `where(column, value)` | Igualdade estrita; `null` → `IS NULL` |
| `where(Consumer)` | Grupo AND |
| `optionalWhere*` | No-op se o valor faltar: `optionalWhere` / `Equal`, `Like` / `Contains` / `StartsWith` / `EndsWith` / `LikePattern`, `In` / `NotIn`, `NotEqual`, `Gt`/`Gte`/`Lt`/`Lte` (+ aliases longos), `Between` / `NotBetween`, extratos de data, `Related*` / `Relation`; OR: `optionalOrWhere` / `Like` / `In` / `NotEqual` / `NotIn` |
| `whereIf` / `when` / `unless` | Condicionais booleanos; `when` com then/else (Eloquent); `unless` = `when(!condition, …)` |
| `orWhere(...)` | OR Spec / igualdade / grupo |
| `whereNot` | NOT Spec |
| `whereEqual` / `whereEqualIf` / `whereNotEqual` | Igualdade / desigualdade explícitas |
| `whereRelatedEqual` / `whereRelatedLike` | Join + filtro (regras `toPattern` para Like) |
| `whereLike` | LIKE case-insensitive; sem `%`/`_` → contains (`%VALUE%`); se o valor tem `%`/`_` → padrão raw; rejeita blank |
| `whereContains` / `whereStartsWith` / `whereEndsWith` | LIKE de texto livre escapado (`%`/`_` literais); preferível para busca de usuário |
| `whereLikePattern` | Padrão raw tal qual (você fornece os wildcards) |
| `whereIn` / `whereNotIn` | Pertencimento; vazio → disjunção / conjunção |
| `whereNull` / `whereNotNull` | Checagens de null |
| `whereGt` / `whereGte` / `whereLt` / `whereLte` | Comparações (aliases longos `whereGreaterThan*` / `whereLessThan*`) |
| `whereBetween` / `whereNotBetween` | Intervalo inclusivo / intervalo negado |
| `whereDate` / `whereYear` / `whereMonth` / `whereDay` / `whereTime` | Extratos temporais (`cb.function`) |
| `whereColumn` / `orWhereColumn` | Comparação coluna a coluna |
| `whereHas` / `whereDoesntHave` / `orWhereHas` / `orWhereDoesntHave` | Associação existe / ausente (`EXISTS` aninhado opcional) |
| `whereRelation` / `optionalWhereRelation` | Atalho de igualdade em coluna relacionada |
| Sobrecargas do metamodelo | APIs string acima com `SingularAttribute` / `PluralAttribute` (filtros, related, has/doesntHave, `select`, order, fetch) |
| `of(executor)` / `of(executor, filters)` / `query()` | Factory / pontos de entrada do repositório |

<a id="carga-forma"></a>
### Carga / forma

| Método | Descrição |
|--------|-----------|
| `fetch` | LEFT JOIN FETCH to-one; ativa DISTINCT |
| `fetchCollection` | LEFT JOIN FETCH to-many; **não** com page/slice/paginate/chunk |
| `select` | Projeção de propriedades estilo Eloquent (`project`); melhor com `*As` |
| `distinct` | Forçar DISTINCT |
| `limit` | Máximo de linhas em terminais de lista |
| `orderByAsc` / `orderByDesc` / `orderBy` | Ordenação |

<a id="terminais-api"></a>
### Terminais

| Método | Descrição |
|--------|-----------|
| `first` / `firstOrNull` / `firstOrFail` | Primeira linha (`LIMIT 1`); OK se houver várias; `*OrFail` lança se vazio |
| `latest` / `latestOrNull` | `orderByDesc` + first |
| `oldest` / `oldestOrNull` | `orderByAsc` + first |
| `one` / `oneOrNull` / `oneOrFail` | Exatamente 0–1 linha; **lança se houver 2+** (`IncorrectResultSizeDataAccessException`); `*OrFail` lança se vazio |
| `get` | Lista (respeita `limit`) |
| `page` | Página com COUNT |
| `paginate` | Página por índice 0-based + size |
| `slice` | Slice sem COUNT |
| `chunk` | Batch via slice |
| `stream` | Stream closable |
| `firstAs` / `getAs` / `pageAs` | Projeções |
| `exists` / `count` | Agregados sem fetch |
| `delete` | Apaga linhas correspondentes (`CrudRepository#deleteAll`) |
| `toSpecification` / `toSelectSpecification` / `toSort` | Inspecionar composição |

<a id="desenvolvimento"></a>
## Desenvolvimento

```text
spring-fluent-query/
├── spring-fluent-query-core/
├── spring-fluent-query-spring-boot-starter/
└── spring-fluent-query-example/    ← demo mínima
```

```bash
# Build e todos os testes (BOM Boot 3)
docker compose run --rm maven

# BOM Boot 4
docker compose run --rm maven mvn clean verify -Pboot4

# Só testes do core
docker compose run --rm maven mvn -pl spring-fluent-query-core test

# App de exemplo
docker compose up example

# Install no .m2 local
docker compose run --rm maven mvn clean install
```

Sem Docker:

```bash
mvn clean verify
mvn clean verify -Pboot4
mvn -pl spring-fluent-query-example spring-boot:run
```

Os releases são publicados no Maven Central — ver [PUBLISHING.md](PUBLISHING.md) (maintainers).

<a id="roadmap"></a>
## Roadmap

- Módulo example mais rico (REST + queries de amostra)
- `autoPublish=true` no Central Portal quando a automação de release estiver estável

<a id="licenca"></a>
## Licença

Copyright © 2026 **Benjamín Olvera R.**

Licenciado sob a [Apache License, Version 2.0](LICENSE).
