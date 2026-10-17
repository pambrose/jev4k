package com.pambrose.jev4k.internal

// Snapshots that Java can't change either. A read-only Kotlin type is only a view: toMap(), toSet() and toList() give
// Java a plain LinkedHashMap, LinkedHashSet or ArrayList once there are two entries, which it can change, while one
// entry gives an immutable one, so the behavior also depended on the size. The collections the build functions
// return reject every change at any size, through their keys, values, entries and iterators too, and keep the order.

internal fun <K, V> Map<K, V>.readOnlyMap(): Map<K, V> = buildMap { putAll(this@readOnlyMap) }

internal fun <E> Iterable<E>.readOnlySet(): Set<E> = buildSet { addAll(this@readOnlySet) }

internal fun <E> Iterable<E>.readOnlyList(): List<E> = buildList { addAll(this@readOnlyList) }
