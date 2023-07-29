/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.rust.codegen.server.smithy

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import software.amazon.smithy.aws.traits.DataTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.SourceLocation
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.RequiredTrait
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.rust.codegen.core.rustlang.RustModule
import software.amazon.smithy.rust.codegen.core.rustlang.RustWriter
import software.amazon.smithy.rust.codegen.core.rustlang.Writable
import software.amazon.smithy.rust.codegen.core.smithy.RuntimeConfig
import software.amazon.smithy.rust.codegen.core.smithy.RuntimeCrateLocation
import software.amazon.smithy.rust.codegen.core.smithy.RustCrate
import software.amazon.smithy.rust.codegen.core.smithy.transformers.OperationNormalizer
import software.amazon.smithy.rust.codegen.core.testutil.asSmithyModel
import software.amazon.smithy.rust.codegen.core.testutil.generatePluginContext
import software.amazon.smithy.rust.codegen.core.testutil.unitTest
import software.amazon.smithy.rust.codegen.core.util.runCommand
import software.amazon.smithy.rust.codegen.core.util.toPascalCase
import software.amazon.smithy.rust.codegen.core.util.toSnakeCase
import software.amazon.smithy.rust.codegen.server.smithy.customizations.CustomValidationExceptionWithReasonDecorator
import software.amazon.smithy.rust.codegen.server.smithy.customizations.ServerRequiredCustomizations
import software.amazon.smithy.rust.codegen.server.smithy.customizations.SmithyValidationExceptionDecorator
import software.amazon.smithy.rust.codegen.server.smithy.customize.CombinedServerCodegenDecorator
import software.amazon.smithy.rust.codegen.server.smithy.testutil.serverTestCodegenContext
import software.amazon.smithy.rust.codegen.server.smithy.transformers.ConstrainedMemberTransform
import java.io.File
import java.nio.file.Path

class ConstraintsMemberShapeTest {
    private val outputModelOnly = """
        namespace constrainedMemberShape

        use aws.protocols#restJson1
        use aws.api#data

        @restJson1
        service ConstrainedService {
            operations: [OperationUsingGet]
        }

        @http(uri: "/anOperation", method: "GET")
        operation OperationUsingGet {
            output : OperationUsingGetOutput
        }
        structure OperationUsingGetOutput {
            plainLong : Long
            plainInteger : Integer
            plainShort : Short
            plainByte : Byte
            plainFloat: Float
            plainString: String

            @range(min: 1, max:100)
            constrainedLong : Long
            @range(min: 2, max:100)
            constrainedInteger : Integer
            @range(min: 3, max:100)
            constrainedShort : Short
            @range(min: 4, max:100)
            constrainedByte : Byte
            @length(max: 100)
            constrainedString: String

            @required
            @range(min: 5, max:100)
            requiredConstrainedLong : Long
            @required
            @range(min: 6, max:100)
            requiredConstrainedInteger : Integer
            @required
            @range(min: 7, max:100)
            requiredConstrainedShort : Short
            @required
            @range(min: 8, max:100)
            requiredConstrainedByte : Byte
            @required
            @length(max: 101)
            requiredConstrainedString: String

            patternString : PatternString

            @data("content")
            @pattern("^[g-m]+${'$'}")
            constrainedPatternString : PatternString

            plainStringList : PlainStringList
            patternStringList : PatternStringList
            patternStringListOverride : PatternStringListOverride

            plainStructField : PlainStructWithInteger
            structWithConstrainedMember : StructWithConstrainedMember
            structWithConstrainedMemberOverride : StructWithConstrainedMemberOverride

            patternUnion: PatternUnion
            patternUnionOverride: PatternUnionOverride
            patternMap : PatternMap
            patternMapOverride: PatternMapOverride
        }
        list ListWithIntegerMemberStruct {
            member: PlainStructWithInteger
        }
        structure PlainStructWithInteger {
            lat : Integer
            long : Integer
        }
        structure StructWithConstrainedMember {
            @range(min: 100)
            lat : Integer
            long : Integer
        }
        structure StructWithConstrainedMemberOverride {
            @range(min: 10)
            lat : RangedInteger
            @range(min: 10, max:100)
            long : RangedInteger
        }
        list PlainStringList {
            member: String
        }
        list PatternStringList {
            member: PatternString
        }
        list PatternStringListOverride {
            @pattern("^[g-m]+${'$'}")
            member: PatternString
        }
        map PatternMap {
            key: PatternString,
            value: PatternString
        }
        map PatternMapOverride {
            @pattern("^[g-m]+${'$'}")
            key: PatternString,
            @pattern("^[g-m]+${'$'}")
            value: PatternString
        }
        union PatternUnion {
            first: PatternString,
            second: PatternString
        }
        union PatternUnionOverride {
            @pattern("^[g-m]+${'$'}")
            first: PatternString,
            @pattern("^[g-m]+${'$'}")
            second: PatternString
        }
        @pattern("^[a-m]+${'$'}")
        string PatternString
        @range(min: 0, max:1000)
        integer RangedInteger
    """.asSmithyModel()

    private fun loadModel(model: Model): Model =
        ConstrainedMemberTransform.transform(OperationNormalizer.transform(model))

    @Test
    fun `non constrained fields should not be changed`() {
        val transformedModel = loadModel(outputModelOnly)

        fun checkFieldTargetRemainsSame(fieldName: String) {
            checkMemberShapeIsSame(
                transformedModel,
                outputModelOnly,
                "constrainedMemberShape.synthetic#OperationUsingGetOutput\$$fieldName",
                "constrainedMemberShape#OperationUsingGetOutput\$$fieldName",
            ) {
                "OperationUsingGetOutput$fieldName has changed whereas it is not constrained and should have remained same"
            }
        }

        setOf(
            "plainInteger",
            "plainLong",
            "plainByte",
            "plainShort",
            "plainFloat",
            "patternString",
            "plainStringList",
            "patternStringList",
            "patternStringListOverride",
            "plainStructField",
            "structWithConstrainedMember",
            "structWithConstrainedMemberOverride",
            "patternUnion",
            "patternUnionOverride",
            "patternMap",
            "patternMapOverride",
        ).forEach(::checkFieldTargetRemainsSame)

        checkMemberShapeIsSame(
            transformedModel,
            outputModelOnly,
            "constrainedMemberShape#StructWithConstrainedMember\$long",
            "constrainedMemberShape#StructWithConstrainedMember\$long",
        )
    }

    @Test
    fun `constrained members should have a different target now`() {
        val transformedModel = loadModel(outputModelOnly)
        checkMemberShapeChanged(
            transformedModel,
            outputModelOnly,
            "constrainedMemberShape#PatternStringListOverride\$member",
            "constrainedMemberShape#PatternStringListOverride\$member",
        )

        fun checkSyntheticFieldTargetChanged(fieldName: String) {
            checkMemberShapeChanged(
                transformedModel,
                outputModelOnly,
                "constrainedMemberShape.synthetic#OperationUsingGetOutput\$$fieldName",
                "constrainedMemberShape#OperationUsingGetOutput\$$fieldName",
            ) {
                "constrained member $fieldName should have been changed into a new type."
            }
        }

        fun checkFieldTargetChanged(memberNameWithContainer: String) {
            checkMemberShapeChanged(
                transformedModel,
                outputModelOnly,
                "constrainedMemberShape#$memberNameWithContainer",
                "constrainedMemberShape#$memberNameWithContainer",
            ) {
                "constrained member $memberNameWithContainer should have been changed into a new type."
            }
        }

        setOf(
            "constrainedLong",
            "constrainedByte",
            "constrainedShort",
            "constrainedInteger",
            "constrainedString",
            "requiredConstrainedString",
            "requiredConstrainedLong",
            "requiredConstrainedByte",
            "requiredConstrainedInteger",
            "requiredConstrainedShort",
            "constrainedPatternString",
        ).forEach(::checkSyntheticFieldTargetChanged)

        setOf(
            "StructWithConstrainedMember\$lat",
            "PatternMapOverride\$key",
            "PatternMapOverride\$value",
            "PatternStringListOverride\$member",
        ).forEach(::checkFieldTargetChanged)
    }

    @Test
    fun `extra trait on a constrained member should remain on it`() {
        val transformedModel = loadModel(outputModelOnly)
        checkShapeHasTrait(
            transformedModel,
            outputModelOnly,
            "constrainedMemberShape.synthetic#OperationUsingGetOutput\$constrainedPatternString",
            "constrainedMemberShape#OperationUsingGetOutput\$constrainedPatternString",
            DataTrait("content", SourceLocation.NONE),
        )
    }

    @Test
    fun `required remains on constrained member shape`() {
        val transformedModel = loadModel(outputModelOnly)
        checkShapeHasTrait(
            transformedModel,
            outputModelOnly,
            "constrainedMemberShape.synthetic#OperationUsingGetOutput\$requiredConstrainedString",
            "constrainedMemberShape#OperationUsingGetOutput\$requiredConstrainedString",
            RequiredTrait(),
        )
    }

    private fun runServerCodeGen(model: Model, dirToUse: File? = null, writable: Writable): Path {
        val runtimeConfig =
            RuntimeConfig(runtimeCrateLocation = RuntimeCrateLocation.Path(File("../rust-runtime").absolutePath))

        val (context, dir) = generatePluginContext(
            model,
            runtimeConfig = runtimeConfig,
            overrideTestDir = dirToUse,
        )
        val codegenDecorator =
            CombinedServerCodegenDecorator.fromClasspath(
                context,
                ServerRequiredCustomizations(),
                SmithyValidationExceptionDecorator(),
                CustomValidationExceptionWithReasonDecorator(),
            )

        ServerCodegenVisitor(context, codegenDecorator)
            .execute()

        val codegenContext = serverTestCodegenContext(model)
        val settings = ServerRustSettings.from(context.model, context.settings)
        val rustCrate = RustCrate(
            context.fileManifest,
            codegenContext.symbolProvider,
            settings.codegenConfig,
            codegenContext.expectModuleDocProvider(),
        )

        // We cannot write to the lib anymore as the RustWriter overwrites it, so writing code directly to check.rs
        // and then adding a `mod check;` to the lib.rs
        rustCrate.withModule(RustModule.public("check")) {
            writable(this)
            File("$dir/src/check.rs").writeText(toString())
        }

        val lib = File("$dir/src/lib.rs")
        val libContents = lib.readText() + "\nmod check;"
        lib.writeText(libContents)

        return dir
    }

    @Test
    fun `generate code and check member constrained shapes are in the right modules`() {
        val dir = runServerCodeGen(outputModelOnly) {
            fun RustWriter.testTypeExistsInBuilderModule(typeName: String) {
                unitTest(
                    "builder_module_has_${typeName.toSnakeCase()}",
                    """
                    #[allow(unused_imports)] use crate::output::operation_using_get_output::$typeName;
                    """,
                )
            }

            // All directly constrained members of the output structure should be in the builder module
            setOf(
                "ConstrainedLong",
                "ConstrainedByte",
                "ConstrainedShort",
                "ConstrainedInteger",
                "ConstrainedString",
                "RequiredConstrainedString",
                "RequiredConstrainedLong",
                "RequiredConstrainedByte",
                "RequiredConstrainedInteger",
                "RequiredConstrainedShort",
                "ConstrainedPatternString",
            ).forEach(::testTypeExistsInBuilderModule)

            fun Set<String>.generateUseStatements(prefix: String) =
                this.joinToString(separator = "\n") {
                    "#[allow(unused_imports)] use $prefix::$it;"
                }

            unitTest(
                "map_overridden_enum",
                setOf(
                    "Value",
                    "value::ConstraintViolation as ValueCV",
                    "Key",
                    "key::ConstraintViolation as KeyCV",
                ).generateUseStatements("crate::model::pattern_map_override"),
            )

            unitTest(
                "union_overridden_enum",
                setOf(
                    "First",
                    "first::ConstraintViolation as FirstCV",
                    "Second",
                    "second::ConstraintViolation as SecondCV",
                ).generateUseStatements("crate::model::pattern_union_override"),
            )

            unitTest(
                "list_overridden_enum",
                setOf(
                    "Member",
                    "member::ConstraintViolation as MemberCV",
                ).generateUseStatements("crate::model::pattern_string_list_override"),
            )
        }

        val env = mapOf("RUSTFLAGS" to "-A dead_code")
        "cargo test".runCommand(dir, env)
    }

    /**
     *  Checks that the given member shape:
     *  1. Has been changed to a new shape
     *  2. New shape has the same type as the original shape's target e.g. float Centigrade,
     *     float newType
     */
    private fun checkMemberShapeChanged(
        model: Model,
        baseModel: Model,
        member: String,
        orgModelMember: String,
        lazyMessage: () -> Any = ::defaultError,
    ) {
        val memberId = ShapeId.from(member)
        assert(model.getShape(memberId).isPresent) { lazyMessage }

        val memberShape = model.expectShape(memberId).asMemberShape().get()
        val memberTargetShape = model.expectShape(memberShape.target)
        val orgMemberId = ShapeId.from(orgModelMember)
        assert(baseModel.getShape(orgMemberId).isPresent) { lazyMessage }

        val beforeTransformMemberShape = baseModel.expectShape(orgMemberId).asMemberShape().get()
        val originalTargetShape = model.expectShape(beforeTransformMemberShape.target)

        val extractableConstraintTraits = allConstraintTraits - RequiredTrait::class.java

        // New member shape should not have the overridden constraints on it
        assert(!extractableConstraintTraits.any(memberShape::hasTrait)) { lazyMessage }

        // Target shape has to be changed to a new shape
        memberTargetShape.id.name shouldNotBe beforeTransformMemberShape.target.name

        // Target shape's name should match the expected name
        val expectedName = memberShape.container.name.substringAfter('#') +
            memberShape.memberName.substringBefore('#').toPascalCase()

        memberTargetShape.id.name shouldBe expectedName

        // New shape should have all the constraint traits that were on the member shape,
        // and it should also have the traits that the target type contains.
        val beforeTransformConstraintTraits =
            beforeTransformMemberShape.allTraits.values.filter { allConstraintTraits.contains(it.javaClass) }.toSet()
        val newShapeConstrainedTraits =
            memberTargetShape.allTraits.values.filter { allConstraintTraits.contains(it.javaClass) }.toSet()

        val leftOutConstraintTrait = beforeTransformConstraintTraits - newShapeConstrainedTraits
        assert(
            leftOutConstraintTrait.isEmpty() || leftOutConstraintTrait.all {
                it.toShapeId() == RequiredTrait.ID
            },
        ) { lazyMessage }

        // In case the target shape has some more constraints, which the member shape did not override,
        // then those still need to apply on the new standalone shape that has been defined.
        val leftOverTraits = originalTargetShape.allTraits.values
            .filter { beforeOverridingTrait ->
                beforeTransformConstraintTraits.none {
                    beforeOverridingTrait.toShapeId() == it.toShapeId()
                }
            }
        val allNewShapeTraits = memberTargetShape.allTraits.values.toList()
        assert((leftOverTraits + newShapeConstrainedTraits).all { it in allNewShapeTraits }) { lazyMessage }
    }

    private fun defaultError() = "test failed"

    /**
     * Checks that the given shape has not changed in the transformed model and is exactly
     * the same as the original model
     */
    private fun checkMemberShapeIsSame(
        model: Model,
        baseModel: Model,
        member: String,
        orgModelMember: String,
        lazyMessage: () -> Any = ::defaultError,
    ) {
        val memberId = ShapeId.from(member)
        assert(model.getShape(memberId).isPresent) { lazyMessage }

        val memberShape = model.expectShape(memberId).asMemberShape().get()
        val memberTargetShape = model.expectShape(memberShape.target)
        val originalShape = baseModel.expectShape(ShapeId.from(orgModelMember)).asMemberShape().get()

        // Member shape should not have any constraints on it
        assert(!memberShape.hasConstraintTrait()) { lazyMessage }
        // Target shape has to be same as the original shape
        memberTargetShape.id shouldBe originalShape.target
    }

    private fun checkShapeHasTrait(
        model: Model,
        orgModel: Model,
        member: String,
        orgModelMember: String,
        trait: Trait,
    ) {
        val memberId = ShapeId.from(member)
        val memberShape = model.expectShape(memberId).asMemberShape().get()
        val orgMemberShape = orgModel.expectShape(ShapeId.from(orgModelMember)).asMemberShape().get()

        val newMemberTrait = memberShape.expectTrait(trait::class.java)
        val oldMemberTrait = orgMemberShape.expectTrait(trait::class.java)

        newMemberTrait shouldBe oldMemberTrait
    }
}
