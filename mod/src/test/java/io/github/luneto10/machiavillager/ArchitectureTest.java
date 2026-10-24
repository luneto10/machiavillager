package io.github.luneto10.machiavillager;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the onion architecture: dependencies point inward only.
 * Inner layers (domain, application) must not depend on outer adapters
 * (minecraft, bridge, client), and domain must not depend on application.
 */
@AnalyzeClasses(packages = "io.github.luneto10.machiavillager", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule innerLayersDoNotUseMinecraft = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage("net.minecraft..", "net.fabricmc..", "com.mojang..")
            .allowEmptyShould(true)
            .because("domain and application logic must stay pure Java so it can be unit tested without starting Minecraft");

    @ArchTest
    static final ArchRule domainDependsOnNothingOutward = noClasses()
            .that().resideInAnyPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..minecraft..", "io.github.luneto10.machiavillager.bridge..", "..client..")
            .allowEmptyShould(true)
            .because("domain is the innermost layer and must not know about use cases or adapters");

    @ArchTest
    static final ArchRule applicationDoesNotUseAdapters = noClasses()
            .that().resideInAnyPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..minecraft..", "io.github.luneto10.machiavillager.bridge..", "..client..")
            .allowEmptyShould(true)
            .because("use cases must depend only on domain and ports, never on concrete adapters");
}
