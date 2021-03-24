package com.apollographql.apollo3.compiler.unified.codegen.helpers

import com.apollographql.apollo3.compiler.applyIf
import com.apollographql.apollo3.compiler.backend.codegen.adapterPackageName
import com.apollographql.apollo3.compiler.backend.codegen.kotlinNameForResponseAdapter
import com.apollographql.apollo3.compiler.backend.codegen.makeDataClassFromProperties
import com.apollographql.apollo3.compiler.unified.IrField
import com.apollographql.apollo3.compiler.unified.IrFieldSet
import com.apollographql.apollo3.compiler.unified.ModelPath
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

fun ModelPath.typeName(): ClassName {
  return ClassName(
      packageName = packageName,
      simpleNames = elements
  )
}

/**
 * The kotlin [TypeName] decorated with nullable/list as needed
 */
fun IrField.typeName(): TypeName {
  return type.typeName()
}

/**
 * The [TypeName] with all nullable/list removed
 */
fun IrField.rawTypeName(): TypeName {
  return type.leafType().typeName().copy(nullable = false)
}

fun IrFieldSet.typeName(): TypeName {
  return fullPath.typeName()
}

fun IrFieldSet.adapterTypeName(): TypeName {
  // Go from:
  // [TestQuery, Data, Hero, ...]
  // To:
  // [TestQuery_ResponseAdapter, Data, Hero, ...]
  return ClassName(
      packageName = adapterPackageName(fullPath.packageName),
      listOf(kotlinNameForResponseAdapter(fullPath.elements.first())) + fullPath.elements.drop(1)
  )
}

fun IrField.typeSpecs(asInterface: Boolean): List<TypeSpec> {
  if (asInterface) {
    return listOfNotNull(baseFieldSet?.typeSpec(true))
  }
  val interfacesTypeSpecs = fieldSets.map { it.typeSpec(true) }
  val implementationTypeSpecs = implementationFieldSets.map { it.typeSpec(false) }

  return interfacesTypeSpecs + implementationTypeSpecs
}

fun IrFieldSet.typeSpec(asInterface: Boolean): TypeSpec {
  val properties = fields.map {
    PropertySpec.builder(it.responseName, it.typeName())
        .applyIf(it.override) { addModifiers(KModifier.OVERRIDE) }
        .maybeAddDescription(it.description)
        .maybeAddDeprecation(it.deprecationReason)
        .build()
  }

  val nestedTypes = fields.flatMap {
    it.typeSpecs(asInterface)
  }

  val superInterfaces = implements.map { it.typeName() }

  return if (asInterface) {
    TypeSpec.interfaceBuilder(modelName)
        .addProperties(properties)
        .addTypes(nestedTypes)
        .addSuperinterfaces(superInterfaces)
        .build()
  } else {
    TypeSpec.classBuilder(modelName)
        .makeDataClassFromProperties(properties)
        .addTypes(nestedTypes)
        .addSuperinterfaces(superInterfaces)
        .build()
  }
}