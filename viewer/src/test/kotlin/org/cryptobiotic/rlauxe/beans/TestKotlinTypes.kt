package org.cryptobiotic.rlauxe.beans

import kotlin.test.Test

class TestKotlinTypes {

    // // Kotlin KClass reference (kotlin.reflect.KClass)
    //val kotlinClass = Int::class
    //
    //// Java primitive class reference (returns: int)
    //val javaPrimitiveClass = Int::class.java
    //
    //// Java boxed object class reference (returns: class java.lang.Integer)
    //val javaBoxedClass = Int::class.javaObjectType
    @Test
    fun testTypes() {
        showWrap(Int::class.java)
        showWrap(Int::class.javaObjectType)
        showWrap(Boolean::class.java)
        showWrap(Boolean::class.javaObjectType)
    }
}

fun showWrap(c: Class<*>) {
    // println("1: $c -> ${wrapPrimitives(c)}")
    println("2: $c -> ${wrapPrimitives2(c)}")
}


fun wrapPrimitives(c: Class<*>): Class<*> {
    if (c == Boolean::class.javaPrimitiveType) return Boolean::class.java
    else if (c == Int::class.javaPrimitiveType) return Int::class.java
    else if (c == Float::class.javaPrimitiveType) return Float::class.java
    else if (c == Double::class.javaPrimitiveType) return Double::class.java
    else if (c == Short::class.javaPrimitiveType) return Short::class.java
    else if (c == Long::class.javaPrimitiveType) return Long::class.java
    else if (c == Byte::class.javaPrimitiveType) return Byte::class.java
    else return c
}

fun wrapPrimitives2(c: Class<*>): Class<*> {
    if (c == Boolean::class.javaPrimitiveType) return Boolean::class.javaObjectType
    else if (c == Int::class.javaPrimitiveType) return Int::class.javaObjectType
    else if (c == Float::class.javaPrimitiveType) return Float::class.javaObjectType
    else if (c == Double::class.javaPrimitiveType) return Double::class.javaObjectType
    else if (c == Short::class.javaPrimitiveType) return Short::class.javaObjectType
    else if (c == Long::class.javaPrimitiveType) return Long::class.javaObjectType
    else if (c == Byte::class.javaPrimitiveType) return Byte::class.javaObjectType
    else return c
}