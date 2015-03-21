/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.platform.common.fp.data;

import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.cloudway.platform.common.fp.function.TriFunction;

/**
 * <p>An efficient implementation of maps from keys to values.</p>
 *
 * <p>The implementation of map is based on size balanced binary trees (or trees
 * of bounded balance) as described by:</p>
 *
 * <ul>
 * <li>Stephen Adams, <a href="http://www.swiss.ai.mit.edu/~adams/BB/">
 *     "Efficient sets: a balancing act"</a>, Journal of Functional Programming
 *     3(4):553-562, October 1993,</li>
 * <li>J. Nievergelt and E.M. Reingold, "Binary search trees of bounded balance",
 *     SIAM journal of computing 2(1), March 1973.</li>
 * </ul>
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public interface TreeMap<K, V> extends Iterable<Map.Entry<K, V>> {
    // Construction

    /**
     * Construct an empty map, sorted according to the natural ordering
     * of its elements.
     */
    @SuppressWarnings("unchecked")
    static <K extends Comparable<K>, V> TreeMap<K, V> empty() {
        return Tree.EMPTY_MAP;
    }

    /**
     * Construct an empty map, sorted according to the specified comparator.
     *
     * @param c the comparator that will be used to order this map
     * @throws NullPointerException if {@code c} is null
     */
    static <K, V> TreeMap<K, V> empty(Comparator<? super K> c) {
        Objects.requireNonNull(c);
        return new Tree.MapTip<>(c);
    }

    /**
     * Construct a map with a single element, sorted according to the natural
     * ordering of its elements.
     */
    static <K extends Comparable<K>, V> TreeMap<K, V> singleton(K key, V value) {
        return TreeMap.<K,V>empty().put(key, value);
    }

    /**
     * Construct a map with a single element, sorted according to the specified
     * comparator.
     *
     * @param c the comparator that will be used to order this map
     * @throws NullPointerException if {@code c} is null
     */
    static <K, V> TreeMap<K, V> singleton(Comparator<? super K> c, K key, V value) {
        return TreeMap.<K,V>empty(c).put(key, value);
    }

    // Query Operations

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map contains no key-value mappings
     */
    boolean isEmpty();

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    int size();

    /**
     * Returns {@code true} if this map contains a mapping for the specified
     * key.
     *
     * @param key key whose presence in this map is to be tested
     * @return {@code true} if this map contains a mapping for the specified key
     */
    boolean containsKey(K key);

    /**
     * Lookup the value to which the specified key is mapped.  Returns
     * {@code Optional.empty()} if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     *         {@code Optional.empty()} if this map contains no mapping
     *         for the key
     */
    Optional<V> lookup(K key);

    /**
     * Returns the value to which the specified key is mapped. If this map contains
     * no mapping for the key, a NoSuchElementException is thrown.
     *
     * @param key the key whose associated value is to returned
     * @return the value to which specified key is mapped
     * @throws NoSuchElementException if this map contains no mapping for the key
     */
    V get(K key);

    /**
     * Returns the value to which the specified key is mapped, or default value
     * if this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @param def the default mapping of the key
     * @return the map that contains new mappings, the original map is unchanged
     */
    V getOrDefault(K key, V def);

    // Modification Operations

    /**
     * Insert a new key and value in the map. If the key is already present
     * in the map, the associated value is replaced with the supplied value.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the map that contains new mappings, the original map is unchanged
     */
    TreeMap<K, V> put(K key, V value);

    /**
     * Removes the mapping for a key from this map if it is present.
     *
     * @param key key with which the specified value is associated
     * @return the map that contains new mappings, the original map is unchanged
     */
    TreeMap<K, V> remove(K key);

    /**
     * Replaces the entry for the specified key only if currently mapped to the
     * specified value.
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     */
    default TreeMap<K, V> replace(K key, V oldValue, V newValue) {
        return computeIfPresent(key, (k, v) ->
            Optional.of(v.equals(oldValue) ? newValue : oldValue));
    }

    /**
     * Replaces the entry for the specified key only if it is currently mapped
     * to some value.
     *
     * @param key key with which the specified value is associated
     * @param value value to be associated with the specified key
     */
    default TreeMap<K, V> replace(K key, V value) {
        return computeIfPresent(key, (k, v) -> Optional.of(value));
    }

    /**
     * Insert a new key and value in the map if it is not already present.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the map that contains new mappings, the original map is unchanged
     */
    TreeMap<K, V> putIfAbsent(K key, V value);

    /**
     * If the specified key is not already associated with a value, attempt to
     * compute its value using the given mapping function and enters it into
     * this map.
     *
     * @param key key with which the specified value is to be associated
     * @param mappingFunction the function to compute a value
     * @return the map that contains new mappings, the original map is unchanged
     */
    TreeMap<K, V> computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

    /**
     * If the value for the specified key is present, attempt to compute a new
     * mapping given the key and its current mapped value.
     *
     * <p>If the function returns {@code Optional.empty()}, the mapping is removed.
     * If the function itself throws an (unchecked) exception, the exception
     * is rethrown.</p>
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     */
    TreeMap<K, V> computeIfPresent(K key,
            BiFunction<? super K, ? super V, Optional<? extends V>> remappingFunction);

    /**
     * Attempts to compute a mapping for the specified key and its current mapped
     * mapped value.
     *
     * <p>If the function returns {@code Optional.empty()}, the mapping is removed
     * (or remains absent if initially absent). If the function itself throws an
     * (unchecked) exception, the exception is rethrown.</p>
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     */
    TreeMap<K, V> compute(K key,
            BiFunction<? super K, Optional<V>, Optional<? extends V>> remappingFunction);

    /**
     * If the specified key is not already associated with a value, associate
     * it with the given value. Otherwise, replaces the associated value with
     * the result of the given remapping function. This method may be of use
     * when combining multiple mapped values for a key. For example, to either
     * create or append a {@code String msg} to a value mapping:
     *
     * <pre> {@code
     * map.merge(key, msg, String::concat)
     * }</pre>
     *
     * @param key key with which the resulting value is to be associated
     * @param value the non-null value to be merged with the existing value
     *        associated with the key or, if no existing value is associated
     *        with the key, to be associated with the key
     * @param remappingFunction the function to recompute a value if present
     * @return the map that contains new mappings, the original map is unchanged
     */
    TreeMap<K, V> merge(K key, V value,
            BiFunction<? super V, ? super V, ? extends V> remappingFunction);

    // Bulk Operations

    /**
     * Returns <tt>true</tt> if this map contains all of the mappings of the
     * specified map.
     *
     * @param m map to be checked for containment in this map
     * @return <tt>true</tt> if this map contains all of the elements of the
     *         specified map
     */
    boolean containsAll(TreeMap<K,V> m);

    /**
     * Unions all of the mappings from the specified map to this map.
     *
     * @param m mappings to be union in this map
     * @return the union of two maps
     */
    TreeMap<K, V> putAll(TreeMap<K, V> m);

    /**
     * Replaces each entry's value with the result of invoking the given
     * function on that entry's value until all entries have been processed.
     *
     * @param f the function to apply to each entry value
     */
    default <R> TreeMap<K,R> map(Function<? super V, ? extends R> f) {
        return mapKV((k, v) -> f.apply(v));
    }

    /**
     * Replaces each entry's value with the result of invoking the given
     * function on that entry's key and value until all entries have been
     * processed.
     *
     * @param f the function to apply to each entry
     */
    <R> TreeMap<K,R> mapKV(BiFunction<? super K, ? super V, ? extends R> f);

    /**
     * Returns a map consisting of the mappings of this map that matches
     * the given predicate.
     *
     * @param predicate a predicate to apply to each map entry value to
     * determine if it should be included
     */
    default TreeMap<K,V> filter(Predicate<? super V> predicate) {
        return filterKV((k, v) -> predicate.test(v));
    }

    /**
     * Returns a map consisting of the mappings of this map that matches
     * the given predicate.
     *
     * @param predicate a predicate to apply to each map entry key and value
     * to determine if it should be included
     */
    TreeMap<K,V> filterKV(BiPredicate<? super K, ? super V> predicate);

    /**
     * Removes all of the mappings of this map that satisfy the given predicate.
     *
     * @param predicate a predicate which returns {@code true} for mappings to
     * be removed
     */
    default TreeMap<K,V> removeIf(BiPredicate<? super K, ? super V> predicate) {
        return filterKV(predicate.negate());
    }

    /**
     * Removes all of the mappings of this map that the key satisfy the given
     * predicate.
     *
     * @param predicate a predicate which returns {@code true} for keys to be
     * removed
     */
    default TreeMap<K,V> removeKeys(Predicate<? super K> predicate) {
        return filterKV((k, v) -> !predicate.test(k));
    }

    /**
     * Removes all of the mappings of this map that the value satisfy the given
     * predicate.
     *
     * @param predicate a predicate which returns {@code true} for values to be
     * removed
     */
    default TreeMap<K,V> removeValues(Predicate<? super V> predicate) {
        return filter(predicate.negate());
    }

    /**
     * Removes all of the mappings of this map that the value equals to the given
     * value.
     *
     * @param value a value for which to be removed
     */
    default TreeMap<K,V> removeValues(V value) {
        return filter(x -> !Objects.equals(x, value));
    }

    /**
     * Perform the given action for each entry in this map until all entries
     * have been processed or the action throws an exception.
     *
     * @param action an action to perform on each elements
     */
    default void forEach(BiConsumer<? super K, ? super V> action) {
        foldLeftKV(Unit.U, (z, k, v) -> { action.accept(k, v); return z; });
    }

    // Views

    /**
     * Returns a set of the keys contained in this map.
     *
     * @return a set of the keys contained in this map
     */
    TreeSet<K> keySet();

    /**
     * Returns a list of the keys contained in this map.
     *
     * @return a list of the keys contained in this map
     */
    default Seq<K> keys() {
        return foldRightKV(Seq.nil(), (k, v, ks) -> Seq.cons(k, ks));
    }

    /**
     * Returns a list of of the values contained in this map.
     *
     * @return a list of of the values contained in this map
     */
    default Seq<V> values() {
        return foldRight(Seq.nil(), Seq::cons);
    }

    /**
     * Returns a list of the mappings contained in this map.
     *
     * @return a list of the mappings contained in this map
     */
    Seq<Map.Entry<K,V>> entries();

    // Folding

    /**
     * Reduce the map elements using the accumulator function that accept
     * element value, from left to right.
     */
    <R> R foldLeft(R seed, BiFunction<R, ? super V, R> accumulator);

    /**
     * Reduce the map elements using the accumulator function that accept
     * element key and value, from left to right.
     */
    <R> R foldLeftKV(R seed, TriFunction<R, ? super K, ? super V, R> accumulator);

    /**
     * Reduce the map elements using the accumulator function that accept
     * element value, from right to left. This is a lazy operation so the
     * accumulator accept a delay evaluation of reduced result instead of
     * a strict value.
     */
    <R> R foldRight(R seed, BiFunction<? super V, Supplier<R>, R> accumulator);

    /**
     * Reduce the map elements using the accumulator function that accept
     * element key and value, from right to left. This is a lazy operation
     * so the accumulator accept a delay evaluation of reduced result instead
     * of a strict value.
     */
    <R> R foldRightKV(R seed, TriFunction<? super K, ? super V, Supplier<R>, R> accumulator);

    /**
     * The strict version of {@link #foldRight(Object,BiFunction) foldRight}.
     */
    <R> R foldRight_(R seed, BiFunction<? super V, R, R> accumulator);

    /**
     * the strict version of {@link #foldRightKV(Object,TriFunction) foldRightKV}.
     */
    <R> R foldRightKV_(R seed, TriFunction<? super K, ? super V, R, R> accumulator);

    // Navigation

    /**
     * Returns a key-value mapping associated with the greatest key strictly
     * less than the given key, or {@code Optional.empty()} if there is no
     * such key.
     *
     * @param key the key
     * @return an entry with the greatest key less than {@code key}, or
     *         {@code Optional.empty()} if there is no such key
     */
    Optional<Map.Entry<K, V>> lowerEntry(K key);

    /**
     * Returns the greatest key strictly less than the given key, or
     * {@code Optional.empty()} if there is no such key.
     *
     * @param key the key
     * @return the greatest key less than {@code key}, or {@code Optional.empty}
     *         if there is no such key
     */
    Optional<K> lowerKey(K key);

    /**
     * Returns a key-value mapping associated with the greatest key less than
     * or equal to the given key, or {@code Optional.empty()} if there is no
     * such key.
     *
     * @param key the key
     * @return an entry with the greatest key less than or equal to {@code key},
     *         or {@code Optional.empty()} if there is no such key
     */
    Optional<Map.Entry<K, V>> floorEntry(K key);

    /**
     * Returns the greatest key less than or equal to the given key, or
     * {@code Optional.empty()} if there is no such key.
     *
     * @param key the key
     * @return the greatest key less than or equal to {@code key}, or
     *         {@code Optional.empty()} if there is no such key
     */
    Optional<K> floorKey(K key);

    /**
     * Returns a key-value mapping associated with the least key greater than
     * or equal to the given key, or {@code Optional.empty()} if there is no
     * such key.
     *
     * @param key the key
     * @return an entry with the least key greater than or equal to {@code key},
     *         or {@code Optional.empty()} if there is no such key
     */
    Optional<Map.Entry<K, V>> ceilingEntry(K key);

    /**
     * Returns the least key greater than or equal to the given key, or
     * {@code Optional.empty()} if there is no such key.
     *
     * @param key the key
     * @return the least key greater than or equal to {@code key}, or
     *         {@code Optional.empty()} if there is no such key
     */
    Optional<K> ceilingKey(K key);

    /**
     * Returns a key-value mapping associated with the least key strictly greater
     * than the given key, or {@code Optional.empty()} if there is no such key.
     *
     * @param key the key
     * @return an entry with the least key greater than {@code key}, or
     *         {@code Optional.empty()} if there is no such key
     */
    Optional<Map.Entry<K, V>> higherEntry(K key);

    /**
     * Returns the least key strictly greater than the given key, or
     * {@code Optional.empty()} if there is no such key.
     *
     * @param key the key
     * @return the least key greater than {@code key}, or {@code Optional.empty()}
     *         if there is no such key
     */
    Optional<K> higherKey(K key);

    /**
     * Returns a key-value mapping associated with the least key in this map.
     *
     * @return an entry with the least key
     * @throws NoSuchElementException if this map is empty
     */
    Map.Entry<K, V> firstEntry();

    /**
     * Returns the first (lowest) key currently in this map.
     *
     * @return the first (lowest) key currently in this map
     * @throws NoSuchElementException if this map is empty
     */
    K firstKey();

    /**
     * Returns a key-value mapping associated with the greatest key in this map.
     *
     * @return an entry with the greatest key
     * @throws NoSuchElementException if this map is empty
     */
    Map.Entry<K, V> lastEntry();

    /**
     * Returns the last (highest) key currently in this map.
     *
     * @return the last (highest) key currently in this map
     * @throws NoSuchElementException if this map is empty
     */
    K lastKey();

    // Debugging

    /**
     * Show the tree that implements the map. This method is used for debugging
     * purposes only.
     */
    String showTree();

    /**
     * Shows the tree that implements the map. Elements are shown using the
     * {@code showElem} function. This method is used for debugging purposes only.
     */
    String showTree(BiFunction<? super K, ? super V, String> showElem);

    /**
     * Test if the internal map structure is valid.
     */
    boolean valid();
}
