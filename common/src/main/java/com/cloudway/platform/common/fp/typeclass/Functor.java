/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.platform.common.fp.typeclass;

import java.util.function.Function;

import com.cloudway.platform.common.fp.data.Fn;

/**
 * Uniform action over a parameterized type, generalizing the map function
 * on lists.
 *
 * <p>The Functor typeclass is used for types that can be mapped over.
 * Instances of Functor should satisfy the following laws:
 *
 * <pre>{@code
 * map id == id
 * map (f . g) == map f . map g
 * }</pre>
 * @param <F> type functor typeclass
 */
public interface Functor<F> {
    /**
     * Returns a container consisting of the results of applying the given
     * function to the elements of given container.
     *
     * <pre>{@code map :: (a -> b) -> f a -> f b}</pre>
     */
    <A, B> $<F,B> map($<F,A> a, Function<? super A, ? extends B> f);

    /**
     * Replace all locations in the input with the same value. The default
     * implementation is {@code map . const}, but this may be overridden with
     * a more efficient version.
     *
     * <pre>{@code (<$) :: f a -> b -> f b}</pre>
     */
    default <A, B> $<F,B> fill($<F,A> a, B b) {
        return map(a, Fn.pure(b));
    }
}
