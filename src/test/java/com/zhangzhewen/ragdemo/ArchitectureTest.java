package com.zhangzhewen.ragdemo;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** COLA light 包依赖方向保护。 */
@AnalyzeClasses(packages="com.zhangzhewen.ragdemo",importOptions=ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    /** domain 不依赖其他业务层。 */ @ArchTest static final ArchRule domain=noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("..adapter..","..application..","..infrastructure..");
    /** application 不直接依赖实现层或入口层。 */ @ArchTest static final ArchRule application=noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat().resideInAnyPackage("..adapter..","..infrastructure..");
    /** adapter 不直接使用基础设施实现。 */ @ArchTest static final ArchRule adapter=noClasses().that().resideInAPackage("..adapter..").should().dependOnClassesThat().resideInAPackage("..infrastructure..");
    /** infrastructure 不反向依赖应用或入口。 */ @ArchTest static final ArchRule infrastructure=noClasses().that().resideInAPackage("..infrastructure..").should().dependOnClassesThat().resideInAnyPackage("..adapter..","..application..");
}
