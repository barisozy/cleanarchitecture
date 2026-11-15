package com.barisozy.cleanarchitecture.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import jakarta.persistence.Entity;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dependency rule of the template, executable.
 *
 * <p>The rules run against the production tree. They are intentionally few and direct: each one
 * names a boundary that a reader can also see in the package structure. A violation fails
 * {@code ./gradlew test} with the offending class, so the fix is obvious.
 */
@AnalyzeClasses(packages = "com.barisozy.cleanarchitecture", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "com.barisozy.cleanarchitecture.domain..";
    private static final String APPLICATION = "com.barisozy.cleanarchitecture.application..";
    private static final String INFRASTRUCTURE = "com.barisozy.cleanarchitecture.infrastructure..";
    private static final String WEB = "com.barisozy.cleanarchitecture.web..";
    private static final String PERSISTENCE = "com.barisozy.cleanarchitecture.infrastructure.persistence";

    /** The domain types the web layer may reference: value types and the error mapping. */
    private static final Set<String> WEB_ALLOWED_DOMAIN_TYPES = Set.of(
            "com.barisozy.cleanarchitecture.domain.DomainException",
            "com.barisozy.cleanarchitecture.domain.user.UserId",
            "com.barisozy.cleanarchitecture.domain.todo.TodoId",
            "com.barisozy.cleanarchitecture.domain.todo.TodoStatus",
            "com.barisozy.cleanarchitecture.domain.user.RoleName");

    /** Persistence APIs the web layer must never see. */
    private static final Set<String> PERSISTENCE_PACKAGES = Set.of(
            "jakarta.persistence",
            "org.springframework.data",
            "org.springframework.dao",
            "org.springframework.orm",
            "org.springframework.jdbc");

    /** Calls that would make behaviour depend on the machine clock. */
    private static final DescribedPredicate<JavaCall<?>> CALL_TO_THE_SYSTEM_CLOCK =
            new DescribedPredicate<>("a call to the operating-system clock") {
                @Override
                public boolean test(JavaCall<?> call) {
                    String owner = call.getTarget().getOwner().getFullName();
                    String name = call.getTarget().getName();
                    return ("java.time.Instant".equals(owner) && "now".equals(name))
                            || ("java.time.LocalDate".equals(owner) && "now".equals(name))
                            || ("java.time.LocalDateTime".equals(owner) && "now".equals(name))
                            || ("java.time.OffsetDateTime".equals(owner) && "now".equals(name))
                            || ("java.time.ZonedDateTime".equals(owner) && "now".equals(name))
                            || ("java.lang.System".equals(owner) && "currentTimeMillis".equals(name))
                            || ("java.time.Clock".equals(owner)
                                    && ("systemUTC".equals(name) || "systemDefaultZone".equals(name)));
                }
            };

    /** The domain is the innermost layer and contains only framework-free business code. */
    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks_or_outer_layers = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.validation..",
                    "jakarta.servlet..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..",
                    "org.hibernate..",
                    "org.slf4j..")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage(APPLICATION, INFRASTRUCTURE, WEB);

    /** The application may use Spring for wiring, but no persistence, HTTP or logging API. */
    @ArchTest
    static final ArchRule application_must_not_depend_on_persistence_http_or_outer_layers = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "jakarta.persistence..",
                    "org.springframework.data..",
                    "org.springframework.dao..",
                    "org.springframework.orm..",
                    "org.springframework.jdbc..",
                    "org.hibernate..",
                    "org.springframework.web..",
                    "org.springframework.http..",
                    "jakarta.servlet..",
                    "tools.jackson..",
                    "com.fasterxml.jackson..",
                    "jakarta.validation..",
                    "org.slf4j..")
            .orShould()
            .dependOnClassesThat()
            .resideInAnyPackage(INFRASTRUCTURE, WEB);

    /** Infrastructure adapters implement ports; they never know the HTTP adapter. */
    @ArchTest
    static final ArchRule infrastructure_must_not_depend_on_web = noClasses()
            .that()
            .resideInAPackage(INFRASTRUCTURE)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(WEB);

    /** Controllers use the application services and a small allow-list of domain value types. */
    @ArchTest
    static final ArchRule web_must_not_depend_on_infrastructure_persistence_or_aggregates = classes()
            .that()
            .resideInAPackage(WEB)
            .should(new ArchCondition<>("depend only on allowed layers") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        String targetPackage = target.getPackageName();
                        if (targetPackage.startsWith("com.barisozy.cleanarchitecture.infrastructure")) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " depends on infrastructure type " + target.getName()));
                        }
                        if (targetPackage.startsWith("com.barisozy.cleanarchitecture.domain")
                                && !WEB_ALLOWED_DOMAIN_TYPES.contains(target.getFullName())) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " depends on forbidden domain type " + target.getName()));
                        }
                        if (isPersistencePackage(targetPackage)) {
                            events.add(SimpleConditionEvent.violated(
                                    item, item.getName() + " depends on persistence type " + target.getName()));
                        }
                    }
                }
            });

    /** JPA entities, repository interfaces and query ports live in their architectural layer. */
    @ArchTest
    static final ArchRule ports_and_persistence_must_live_in_their_layer = classes()
            .should(new ArchCondition<>("live in their architectural layer") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    String packageName = item.getPackageName();
                    if (item.isAnnotatedWith(Entity.class) && !packageName.startsWith(PERSISTENCE)) {
                        events.add(SimpleConditionEvent.violated(
                                item, item.getName() + " is a JPA entity outside persistence"));
                    }
                    if (item.isInterface()
                            && item.getSimpleName().endsWith("Repository")
                            && !packageName.startsWith(PERSISTENCE)
                            && !packageName.startsWith("com.barisozy.cleanarchitecture.domain")) {
                        events.add(SimpleConditionEvent.violated(
                                item, item.getName() + " is a repository outside domain"));
                    }
                    if (item.isInterface()
                            && item.getSimpleName().endsWith("QueryPort")
                            && !packageName.startsWith("com.barisozy.cleanarchitecture.application")) {
                        events.add(SimpleConditionEvent.violated(
                                item, item.getName() + " is a query port outside application"));
                    }
                }
            });

    /** Application services are discovered by the component scan. */
    @ArchTest
    static final ArchRule application_services_must_be_annotated_with_service = classes()
            .that()
            .resideInAPackage(APPLICATION)
            .and()
            .haveSimpleNameEndingWith("Service")
            .should()
            .beAnnotatedWith(Service.class);

    /** Transaction boundaries belong to the application services. */
    @ArchTest
    static final ArchRule transactional_code_must_live_in_the_application_layer = classes()
            .should(new ArchCondition<>("declare transactions only in application") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    boolean transactional = item.isAnnotatedWith(Transactional.class)
                            || item.getMethods().stream()
                                    .anyMatch(method -> method.isAnnotatedWith(Transactional.class));
                    if (transactional
                            && !item.getPackageName().startsWith("com.barisozy.cleanarchitecture.application")) {
                        events.add(SimpleConditionEvent.violated(
                                item, item.getName() + " declares a transaction outside application"));
                    }
                }
            });

    /** Time is injected, so domain and application behaviour stays deterministic. */
    @ArchTest
    static final ArchRule domain_and_application_must_not_read_the_system_clock = noClasses()
            .that()
            .resideInAnyPackage(DOMAIN, APPLICATION)
            .should()
            .callMethodWhere(CALL_TO_THE_SYSTEM_CLOCK);

    /** The layer packages form a directed acyclic graph. */
    @ArchTest
    static final ArchRule layer_slices_must_be_free_of_cycles = SlicesRuleDefinition.slices()
            .matching("com.barisozy.cleanarchitecture.(*)..")
            .should()
            .beFreeOfCycles();

    /** Lombok and MapStruct would hide business logic behind annotation-processor magic. */
    @ArchTest
    static final ArchRule lombok_and_mapstruct_must_not_be_used =
            noClasses().should().dependOnClassesThat().resideInAnyPackage("lombok..", "org.mapstruct..");

    /** Nullability is expressed with JSpecify, not with an {@link Optional} field or getter. */
    @ArchTest
    static final ArchRule optional_must_not_be_a_field_or_getter = classes()
            .should(new ArchCondition<>("not expose Optional as a field or getter") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.getFields().stream()
                            .filter(field -> field.getRawType().isEquivalentTo(Optional.class))
                            .forEach(field -> events.add(SimpleConditionEvent.violated(
                                    field, field.getFullName() + " is an Optional field")));
                    item.getMethods().stream()
                            .filter(method -> method.getRawParameterTypes().isEmpty())
                            .filter(method -> method.getName().startsWith("get")
                                    || method.getName().startsWith("is"))
                            .filter(method -> method.getRawReturnType().isEquivalentTo(Optional.class))
                            .forEach(method -> events.add(SimpleConditionEvent.violated(
                                    method, method.getFullName() + " is an Optional getter")));
                }
            });

    private static boolean isPersistencePackage(String packageName) {
        boolean hibernateOrm =
                packageName.startsWith("org.hibernate") && !packageName.startsWith("org.hibernate.validator");
        return hibernateOrm || PERSISTENCE_PACKAGES.stream().anyMatch(packageName::startsWith);
    }
}
