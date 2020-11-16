package software.amazon.smithy.rust.codegen.smithy

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.rust.codegen.lang.Derives
import software.amazon.smithy.rust.codegen.lang.Meta
import software.amazon.smithy.rust.codegen.util.orNull

/**
 * Default delegator to enable easily decorating another symbol provider.
 */
open class WrappingSymbolProvider(private val base: SymbolProvider) : SymbolProvider {
    override fun toSymbol(shape: Shape): Symbol {
        return base.toSymbol(shape)
    }

    override fun toMemberName(shape: MemberShape): String {
        return base.toMemberName(shape)
    }
}

/**
 * Attach `meta` to symbols. `meta` is used by the generators (eg. StructureGenerator) to configure the generated models.
 *
 * Protocols may inherit from this class and override the `xyzMeta` methods to modify structure generation.
 */
open class SymbolMetadataProvider(private val base: SymbolProvider) : WrappingSymbolProvider(base) {
    override fun toSymbol(shape: Shape): Symbol {
        val baseSymbol = base.toSymbol(shape)
        val meta = when (shape) {
            is MemberShape -> memberMeta(shape)
            is StructureShape -> structureMeta(shape)
            is UnionShape -> unionMeta(shape)
            is StringShape -> if (shape.hasTrait(EnumTrait::class.java)) {
                enumMeta(shape)
            } else null
            else -> null
        }
        return baseSymbol.toBuilder().meta(meta).build()
    }

    open fun memberMeta(memberShape: MemberShape): Meta {
        return Meta(public = true)
    }

    open fun structureMeta(structureShape: StructureShape): Meta {
        return Meta(Derives(defaultDerives.toSet()), public = true)
    }

    open fun unionMeta(unionShape: UnionShape): Meta {
        return Meta(Derives(defaultDerives.toSet()), public = true)
    }

    open fun enumMeta(stringShape: StringShape): Meta {
        return Meta(
            Derives(
                defaultDerives.toSet() +
                    // enums must be hashable because string sets are hashable
                    RuntimeType.Std("hash::Hash") +
                    // enums can be eq because they can only contain strings
                    RuntimeType.Std("cmp::Eq")
            ),
            public = true
        )
    }

    companion object {
        private val defaultDerives =
            listOf(RuntimeType.StdFmt("Debug"), RuntimeType.Std("cmp::PartialEq"), RuntimeType.Std("clone::Clone"))
    }
}

private const val MetaKey = "meta"
fun Symbol.Builder.meta(meta: Meta?): Symbol.Builder {
    return this.putProperty(MetaKey, meta)
}
fun Symbol.meta(): Meta? = this.getProperty(MetaKey, Meta::class.java).orNull()
