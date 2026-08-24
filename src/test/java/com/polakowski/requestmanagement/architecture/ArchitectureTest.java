package com.polakowski.requestmanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Executable version of the design decisions, so that the separation between the domain and the
 * infrastructure is enforced by the build rather than by good intentions.
 */
@AnalyzeClasses(
        packages = "com.polakowski.requestmanagement",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers_are_respected = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("com.polakowski.requestmanagement.domain..")
            .layer("Application").definedBy("com.polakowski.requestmanagement.application..")
            .layer("Infrastructure").definedBy("com.polakowski.requestmanagement.infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule the_domain_does_not_know_about_spring = noClasses()
            .that().resideInAPackage("com.polakowski.requestmanagement.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .because("the domain must stay usable, and testable, without a framework");

    @ArchTest
    static final ArchRule the_domain_does_not_know_about_persistence = noClasses()
            .that().resideInAPackage("com.polakowski.requestmanagement.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "javax.persistence..")
            .because("how a request is stored is not a business rule");

    @ArchTest
    static final ArchRule the_domain_does_not_know_about_the_web = noClasses()
            .that().resideInAPackage("com.polakowski.requestmanagement.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.servlet..", "com.fasterxml.jackson..", "io.swagger..")
            .because("the domain must not be shaped by the transport it happens to be exposed over");

    @ArchTest
    static final ArchRule the_application_does_not_know_about_persistence = noClasses()
            .that().resideInAPackage("com.polakowski.requestmanagement.application..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
            .because("the application orchestrates use cases through ports, not through a database");

    @ArchTest
    static final ArchRule entities_stay_in_the_persistence_package = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideInAPackage("com.polakowski.requestmanagement.infrastructure.persistence..")
            .because("entities are a persistence detail");

    @ArchTest
    static final ArchRule controllers_stay_in_the_rest_package = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage("com.polakowski.requestmanagement.infrastructure.rest..");

    @ArchTest
    static final ArchRule collaborators_arrive_through_the_constructor = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .because("dependencies belong in the constructor, where they are visible, final and "
                    + "supplied by a test as easily as by the container");

    @ArchTest
    static final ArchRule there_are_no_cycles = SlicesRuleDefinition.slices()
            .matching("com.polakowski.requestmanagement.(*)..")
            .should().beFreeOfCycles();
}
