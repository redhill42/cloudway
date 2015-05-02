/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.fp.data;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.cloudway.fp.control.Applicative;
import com.cloudway.fp.control.MonadFix;
import com.cloudway.fp.control.MonadPlus;
import com.cloudway.fp.function.ExceptionBiFunction;
import com.cloudway.fp.function.ExceptionFunction;
import com.cloudway.fp.function.ExceptionSupplier;
import com.cloudway.fp.function.ExceptionTriFunction;
import com.cloudway.fp.control.ConditionCase;
import com.cloudway.fp.$;

/**
 * A sequential, ordered, and potentially lazied list.
 *
 * @param <T> the element type
 */
public interface Seq<T> extends $<Seq.µ, T>, Foldable<T>, Traversable<Seq.µ, T>, Forcible<Seq<T>>
{
    /**
     * Returns {@code true} if this list contains no elements.
     *
     * @return {@code true} if this list contains no elements
     */
    boolean isEmpty();

    /**
     * Returns the first element in the list.
     *
     * @return the first element in the list
     */
    T head();

    /**
     * Returns remaining elements in the list.
     *
     * @return remaining elements in the list
     */
    Seq<T> tail();

    /**
     * Peek the head element as a {@code Maybe}.
     *
     * @return an empty {@code Maybe} if the sequence is empty, otherwise
     * a {@code Maybe} wrapping the head value.
     * @throws NullPointerException if the sequence is not empty but the head
     * element is {@code null}.
     */
    default Maybe<T> peek() {
        return isEmpty() ? Maybe.empty() : Maybe.of(head());
    }

    /**
     * Returns <tt>true</tt> if this list contains the specified element.
     *
     * @return <tt>true</tt> if this list contains the specified element
     */
    default boolean contains(T e) {
        return find(x -> Objects.equals(e, x)).isPresent();
    }

    // Constructors

    /**
     * Construct an empty list.
     *
     * @return the empty list
     */
    static <T> Seq<T> nil() {
        return SeqImpl.nil();
    }

    /**
     * Construct a list with head and tail.
     *
     * @param head the first element in the list
     * @param tail the remaining elements in the list
     * @return the list that concatenate from head and tail
     */
    static <T> Seq<T> cons(T head, Seq<T> tail) {
        return SeqImpl.cons(head, tail);
    }

    /**
     * Construct a lazy list with head and a tail generator.
     *
     * @param head the first element in the list
     * @param tail a supplier to generate remaining elements in the list
     * @return the list that concatenate from head and tail
     */
    static <T> Seq<T> cons(T head, Supplier<Seq<T>> tail) {
        return SeqImpl.cons(head, tail);
    }

    /**
     * Construct a lazy list with two head elements and a tail generator.
     *
     * @param x the first element in the list
     * @param y the second element in the list
     * @param ys a supplier to generate remaining elements in the list
     * @return the list that concatenate from heads and tail
     */
    static <T> Seq<T> cons(T x, T y, Supplier<Seq<T>> ys) {
        return cons(x, cons(y, ys));
    }

    /**
     * Construct a list with single element.
     */
    static <T> Seq<T> of(T value) {
        return SeqImpl.single(value);
    }

    /**
     * Construct a list with given elements
     */
    @SafeVarargs
    static <T> Seq<T> of(T... elements) {
        Seq<T> res = nil();
        for (int i = elements.length; --i >= 0; ) {
            res = cons(elements[i], res);
        }
        return res;
    }

    /**
     * Wrap an iterator into a list.
     */
    static <T> Seq<T> wrap(Iterator<T> iterator) {
        return iterator.hasNext()
            ? cons(iterator.next(), () -> wrap(iterator))
            : nil();
    }

    /**
     * Wrap an iterable into a list.
     */
    static <T> Seq<T> wrap(Iterable<T> iterable) {
        return wrap(iterable.iterator());
    }

    /**
     * Wrap a stream into list.
     */
    static <T> Seq<T> wrap(Stream<T> stream) {
        return wrap(stream.iterator());
    }

    /**
     * Wrap a map into a list.
     */
    static <K,V> Seq<Tuple<K,V>> wrap(Map<K,V> map) {
        return wrap(map.entrySet()).map(e -> Tuple.of(e.getKey(), e.getValue()));
    }

    /**
     * Wrap a {@code Maybe} as a sequence. The sequence contains one element if
     * {@code Maybe} contains a value, otherwise the sequence is empty if
     * {@code Maybe} is empty.
     */
    static <T> Seq<T> wrap(Maybe<? extends T> opt) {
        return opt.map(Seq::of).orElse(nil());
    }

    /**
     * Wrap a sequence of characters.
     */
    static Seq<Character> wrap(CharSequence cs) {
        Seq<Character> res = nil();
        for (int i = cs.length(); --i >= 0; ) {
            res = cons(cs.charAt(i), res);
        }
        return res;
    }

    /**
     * Returns an infinite list produced by iterative application of
     * a function {@code f} to an initial element {@code seed}, producing
     * list consisting of {@code seed}, {@code f(seed)}, {@code f(f(seed))},
     * etc.
     */
    static <T> Seq<T> iterate(T seed, UnaryOperator<T> f) {
        return cons(seed, () -> iterate(f.apply(seed), f));
    }

    /**
     * Returns an infinite list where each element is generated by the provided
     * {@code Supplier}. This is suitable for generating constant sequences,
     * sequences of random elements, etc.
     */
    static <T> Seq<T> generate(Supplier<T> s) {
        return cons(s.get(), () -> generate(s));
    }

    /**
     * Create an infinite list where all items are the specified object.
     */
    static <T> Seq<T> repeat(T value) {
        return new SeqImpl.Repeater<>(value);
    }

    /**
     * Create a list of length {@code n} with the value of every element.
     */
    static <T> Seq<T> replicate(int n, T value) {
        return repeat(value).take(n);
    }

    // Deconstructions

    /**
     * Returns a conditional case that will be evaluated if the list to be
     * tested is empty.
     */
    static <T, R, X extends Throwable> ConditionCase<Seq<T>, R, X>
    Nil(ExceptionSupplier<R, X> supplier) {
        return t -> t.isEmpty() ? supplier : null;
    }

    /**
     * Returns a conditional case that will be evaluated if the list is not
     * empty. The mapper function will accept list head and tail as it's
     * arguments.
     */
    static <T, R, X extends Throwable> ConditionCase<Seq<T>, R, X>
    Cons(ExceptionBiFunction<? super T, ? super Seq<T>, ? extends R, X> mapper) {
        return s -> s.isEmpty()
            ? null
            : () -> mapper.evaluate(s.head(), SeqImpl.delay(s));
    }

    /**
     * Returns a conditional case that will be evaluated if the list contains
     * at least two elements. The mapper function will accept first two elements
     * and remaining elements at it's arguments.
     */
    static <T, R, X extends Throwable> ConditionCase<Seq<T>, R, X>
    Cons(ExceptionTriFunction<? super T, ? super T, ? super Seq<T>, ? extends R, X> mapper) {
        return s -> !s.isEmpty() && !s.tail().isEmpty()
            ? () -> mapper.evaluate(s.head(), s.tail().head(), SeqImpl.delay(s.tail()))
            : null;
    }

    /**
     * Returns a conditional case that will be evaluated if the list contains
     * a single value. The mapper function will accept the singleton value as
     * it's arguments.
     */
    static <T, R, X extends Throwable> ConditionCase<Seq<T>, R, X>
    Single(ExceptionFunction<? super T, ? extends R, X> mapper) {
        return s -> !s.isEmpty() && s.tail().isEmpty()
            ? () -> mapper.evaluate(s.head())
            : null;
    }

    // Operations

    /**
     * Fully evaluate the list to "Normal Form".  This include the data
     * structure of the list and elements in list.  This method will not
     * return for a infinite list.
     */
    @Override
    default Seq<T> force() {
        for (Seq<T> xs = this; !xs.isEmpty(); xs = xs.tail()) {
            Forcible.force(xs.head());
        }
        return this;
    }

    /**
     * Repeat a list infinitely.
     */
    @SuppressWarnings("unchecked")
    default Seq<T> cycle() {
        if (isEmpty()) {
            return nil();
        } else {
            return Ref.cycle((Ref<Seq<T>> h) -> SeqImpl.concat(this, h));
        }
    }

    /**
     * Reverse elements in this list.
     */
    default Seq<T> reverse() {
        Seq<T> res = nil();
        for (Seq<T> xs = this; !xs.isEmpty(); xs = xs.tail()) {
            res = cons(xs.head(), res);
        }
        return res;
    }

    /**
     * Concatenate this list to other list.
     */
    default Seq<T> append(Seq<? extends T> other) {
        return SeqImpl.concat(this, other);
    }

    /**
     * Lazily concatenate this list to other list.
     */
    default Seq<T> append(Supplier<? extends Seq<T>> other) {
        return SeqImpl.concat(this, other);
    }

    /**
     * Append a single element at end of this sequence.
     */
    default Seq<T> append(T elem) {
        return append(of(elem));
    }

    /**
     * Returns a list consisting of the elements of this list that match
     * the given predicate
     *
     * @param predicate a predicate to apply to each element to determine if it
     * should be included
     * @return the new list
     */
    default Seq<T> filter(Predicate<? super T> predicate) {
        for (Seq<T> xs = this; !xs.isEmpty(); xs = xs.tail()) {
            if (predicate.test(xs.head())) {
                final Seq<T> t = xs;
                return cons(t.head(), () -> t.tail().filter(predicate));
            }
        }
        return nil();
    }

    /**
     * Returns a list consisting of the results of applying the given function
     * to the elements of this list.
     *
     * @param <R> the element type of the new list
     * @param mapper a function to apply to each element
     * @return the new list
     */
    default <R> Seq<R> map(Function<? super T, ? extends R> mapper) {
        return foldRight(nil(), (x, xs) -> cons(mapper.apply(x), xs));
    }

    /**
     * Returns a list consisting of the results of replacing each element of
     * this list with the contents of a mapped list produced by applying the
     * provided mapping function to each element.
     *
     * @param <R> the element type of the new list
     * @param mapper a function to apply to each element which produces a list
     * of new values
     * @return the new list
     */
    default <R> Seq<R> flatMap(Function<? super T, ? extends Seq<R>> mapper) {
        return foldRight(nil(), (x, xs) -> SeqImpl.concat(mapper.apply(x), xs));
    }

    /**
     * Map each element of this list to an action, evaluate these actions from
     * left to right, and collect the results.
     */
    @Override
    @SuppressWarnings("unchecked")
    default <F, R> $<F, Seq<R>> traverse(Applicative<F> m, Function<? super T, ? extends $<F,R>> f) {
        BiFunction<R, Seq<R>, Seq<R>> cons_f = Seq::cons;
        return foldRight_(m.pure(nil()), (x, ys) -> m.ap2(cons_f, f.apply(x), ys));
    }

    /**
     * Transposes the rows and columns of its argument. For example.
     * <pre>{@code
     *     transpose [[1,2,3],[4,5,6]] == [[1,4],[2,5],[3,6]]
     * }</pre>
     */
    static <T> Seq<Seq<T>> transpose(Seq<Seq<T>> source) {
        Seq<T> head = source.filter(xs -> !xs.isEmpty()).map(Seq::head);
        return head.isEmpty() ? nil() : cons(head, () ->
            transpose(source.map(xs -> xs.isEmpty() ? xs : xs.tail())));
    }

    /**
     * Returns an iterator over elements of this list.
     *
     * @return an iterator
     */
    @Override
    default Iterator<T> iterator() {
        return new Iterator<T>() {
            Seq<T> cur = Seq.this;

            @Override
            public boolean hasNext() {
                return !cur.isEmpty();
            }

            @Override
            public T next() {
                T res = cur.head();
                cur = cur.tail();
                return res;
            }
        };
    }

    /**
     * Performs an action for each element of this list.
     *
     * @param action an action to perform on the elements
     */
    @Override
    default void forEach(Consumer<? super T> action) {
        forEach(this, action);
    }

    /**
     * Performs an action for each element of given sequence. This static method
     * is provided to enable the sequence node to be garbage collected for large
     * sequence during iteration.
     *
     * @param xs the sequence to iterate
     * @param action an action to perform on the elements
     */
    static <T> void forEach(Seq<T> xs, Consumer<? super T> action) {
        for (; !xs.isEmpty(); xs = xs.tail()) {
            action.accept(xs.head());
        }
    }

    /**
     * Returns a sequence consisting of the elements of this sequence, sorted
     * according to natural order. If elements of this sequence are not
     * {@code Comparable}, a {@code java.lang.ClassCastException} may be thrown.
     */
    @SuppressWarnings("unchecked")
    default Seq<T> sorted() {
        return sorted((Comparator<? super T>)Comparator.naturalOrder());
    }

    /**
     * Returns a sequence consisting of the elements of this sequence, sorted
     * according to the provided {@code Comparator}.
     */
    default Seq<T> sorted(Comparator<? super T> comparator) {
        return SeqImpl.sort(this, comparator);
    }

    /**
     * Returns a sequence consisting of the distinct elements (according to
     * {@link Object#equals(Object)}) of this sequence.
     *
     * @return the new sequence
     */
    default Seq<T> distinct() {
        return SeqImpl.distinct(this);
    }

    /**
     * Zip two lists into one list of tuples.
     */
    default <U> Seq<Tuple<T,U>> zip(Seq<? extends U> other) {
        return zip(other, Tuple::of);
    }

    /**
     * Zip two lists into one using a function to produce result values.
     *
     * <pre>{@code
     *     // ("1:a", "2:b", "3:c")
     *     Seq.of(1, 2, 3).zip(Seq.of("a", "b", "c"), (i,s) -> i + ":" + s)
     * }</pre>
     */
    default <U, R> Seq<R> zip(Seq<? extends U> other,
            BiFunction<? super T, ? super U, ? extends R> zipper) {
        return SeqImpl.zip(this, other, zipper);
    }

    /**
     * The static version of {@link #zip(Seq,Seq,BiFunction)}.
     */
    static <T, U, R> Seq<R> zip(Seq<? extends T> xs, Seq<? extends U> ys,
            BiFunction<? super T, ? super U, ? extends R> zipper) {
        return SeqImpl.zip(xs, ys, zipper);
    }

    /**
     * Transforms a list of pairs into a list of first components and a list
     * of second components.
     *
     * @param xs the list of pairs to transform
     * @return a list of first components and a list of second components
     */
    @SuppressWarnings("RedundantTypeArguments")
    static <T, U> Tuple<Seq<T>, Seq<U>> unzip(Seq<Tuple<T, U>> xs) {
        return xs.foldRight(Tuple.of(Seq.<T>nil(), Seq.<U>nil()), (t, r) ->
            Tuple.of(Seq.<T>cons(t.first(), Fn.map(r, Tuple::first)),
                     Seq.<U>cons(t.second(), Fn.map(r, Tuple::second))));
    }

    /**
     * Reduce the list using the binary operator, from right to left. This is
     * a lazy operation so the accumulator accept a delay evaluation of reduced
     * result instead of a strict value.
     */
    @Override
    default <R> R foldRight(BiFunction<? super T, Supplier<R>, R> accumulator, Supplier<R> partial) {
        return isEmpty() ? partial.get()
             : accumulator.apply(head(), Fn.lazy(() -> tail().foldRight(accumulator, partial)));
    }

    /**
     * Reduce the list using the binary operator, from left to right.
     */
    @Override
    default <R> R foldLeft(R identity, BiFunction<R, ? super T, R> accumulator) {
        R result = identity; Seq<T> xs = this;
        while (!xs.isEmpty()) {
            result = accumulator.apply(result, xs.head());
            xs = xs.tail();
        }
        return result;
    }

    @Override
    default Seq<T> asList() {
        return this;
    }

    /**
     * Just like foldLeft but accumulate intermediate accumulator result in the
     * form of a list.
     */
    default <R> Seq<R> scanLeft(R identity, BiFunction<R, ? super T, R> accumulator) {
        return isEmpty()
            ? cons(identity, nil())
            : cons(identity, () -> tail().scanLeft(accumulator.apply(identity, head()), accumulator));
    }

    /**
     * Just like foldRight but accumulate intermediate accumulator result in the
     * form of a list.
     */
    default <R> Seq<R> scanRight(R identity, BiFunction<? super T, Supplier<R>, R> accumulator) {
        return foldRight(of(identity), (x, acc) ->
            cons(accumulator.apply(x, Fn.map(acc, Seq::head)), acc));
    }

    /**
     * A convenient method that collect sequence elements into a List.
     */
    default List<T> toList() {
        return collect(Collectors.toList());
    }

    /**
     * Returns a list with given limited elements taken.
     */
    default Seq<T> take(int n) {
        if (n <= 0) {
            return nil();
        } else {
            Seq<T> xs = SeqImpl.force(this);
            return xs.isEmpty() ? nil() : cons(xs.head(), () -> SeqImpl.delay(xs).take(n - 1));
        }
    }

    /**
     * Returns a list with given number of elements dropped.
     */
    default Seq<T> drop(int n) {
        Seq<T> xs = this;
        while (--n >= 0 && !xs.isEmpty()) {
            xs = xs.tail();
        }
        return xs;
    }

    /**
     * Returns a list with all elements skipped for which a predicate evaluates to {@code true}.
     */
    default Seq<T> takeWhile(Predicate<? super T> predicate) {
        return isEmpty() || !predicate.test(head())
            ? nil()
            : cons(head(), () -> tail().takeWhile(predicate));
    }

    /**
     * Returns a list with all elements skipped for which a predicate evaluates to {@code false}.
     */
    default Seq<T> takeUntil(Predicate<? super T> predicate) {
        return takeWhile(predicate.negate());
    }

    /**
     * Returns a list with all elements dropped for which a predicate evaluates to {@code true}.
     */
    default Seq<T> dropWhile(Predicate<? super T> predicate) {
        for (Seq<T> xs = this; !xs.isEmpty(); xs = xs.tail()) {
            if (!predicate.test(xs.head())) {
                return xs;
            }
        }
        return nil();
    }

    /**
     * Returns a list with all elements dropped for which a predicate evaluates to {@code false}.
     */
    default Seq<T> dropUntil(Predicate<? super T> predicate) {
        return dropWhile(predicate.negate());
    }

    /**
     * Applied to a predicate and returns a tuple where first element is longest
     * prefix (possibly empty) of xs of elements that satisfy the given predicate
     * and second element is the remainder of the list.
     *
     * <p>{@code xs.span(p)} is equivalent to {@code (xs.takeWhile(p), xs.dropWhile(p)}.
     */
    default Tuple<Seq<T>, Seq<T>> span(Predicate<? super T> predicate) {
        SeqZipper<T> z = SeqZipper.from(this);
        while (!z.atEnd() && predicate.test(z.get())) {
            z = z.right();
        }
        return Tuple.of(z.front(), z.rear());
    }

    /**
     * Returns the count of elements in this list.
     *
     * @return the count of elements in this list
     */
    @Override
    default long count() {
        long count = 0;
        for (Seq<T> xs = this; !xs.isEmpty(); xs = xs.tail()) {
            count++;
        }
        return count;
    }

    /**
     * Search for an element that satisfy the given predicate.
     *
     * @param predicate the predicate to be tested on element
     * @return an empty {@code Maybe} if element not found in the list, otherwise
     * a {@code Maybe} wrapping the found element.
     * @throws NullPointerException if found the element but the element is {@code null}
     */
    @Override
    default Maybe<T> find(Predicate<? super T> predicate) {
        for (Seq<T> xs = this; !xs.isEmpty(); xs = xs.tail()) {
            T x = xs.head();
            if (predicate.test(x)) {
                return Maybe.of(x);
            }
        }
        return Maybe.empty();
    }

    /**
     * Returns '[()]' if the given guard condition is true, otherwise returns '[]'.
     * This method can be used in a 'do' notation.
     */
    static Seq<Unit> guard(boolean test) {
        return test ? of(Unit.U) : nil();
    }

    /**
     * Flatten a list of lists.
     */
    static <T> Seq<T> flatten(Seq<Seq<T>> lists) {
        return lists.foldRight(nil(), SeqImpl::concat);
    }

    /**
     * Concatenate an array of lists.
     */
    @SafeVarargs
    static <T> Seq<T> concat(Seq<T>... lists) {
        return flatten(of(lists));
    }

    // Type classes

    /**
     * Returns the {@link Monoid} typeclass.
     */
    static <T> Monoid<Seq<T>> monoid() {
        return Monoid.monoid(nil(), SeqImpl::concat);
    }

    /**
     * Typeclass definition for Seq.
     */
    class µ implements MonadPlus<µ>, MonadFix<µ> {
        private µ() {}

        @Override
        public <A> Seq<A> pure(A a) {
            return of(a);
        }

        @Override
        public <A, B> Seq<B> map($<µ,A> a, Function<? super A, ? extends B> f) {
            return narrow(a).map(f);
        }

        @Override
        public <A, B> Seq<B> bind($<µ,A> a, Function<? super A, ? extends $<µ,B>> k) {
            return narrow(a).flatMap(x -> narrow(k.apply(x)));
        }

        @Override
        public <A> Seq<A> fail(String s) {
            return nil();
        }

        @Override
        public <A> Seq<A> mzero() {
            return nil();
        }

        @Override
        public <A> Seq<A> mplus($<µ, A> a1, $<µ, A> a2) {
            return narrow(a1).append(narrow(a2));
        }

        @Override
        public <A> Seq<A> mfix(Function<Supplier<A>, ? extends $<µ, A>> f) {
            Seq<A> ys = Fn.<Seq<A>>fix(xs -> narrow(f.apply(() -> xs.get().head())));
            if (ys.isEmpty()) {
                return nil();
            } else {
                return cons(ys.head(), () -> this.<A>mfix(x -> narrow(f.apply(x)).tail()));
            }
        }
    }

    static <T> Seq<T> narrow($<µ, T> value) {
        return (Seq<T>)value;
    }

    /**
     * The singleton type class instance.
     */
    µ tclass = new µ();

    /**
     * Returns the type class of Seq.
     */
    @Override
    default µ getTypeClass() {
        return tclass;
    }

    // Convenient static monad methods

    static <T, A> Seq<? extends Traversable<T, A>>
    flatM(Traversable<T, ? extends $<µ, A>> ms) {
        return narrow(tclass.flatM(ms));
    }

    @SuppressWarnings("unchecked")
    static <A> Seq<Seq<A>> flatM(Seq<Seq<A>> ms) {
        return (Seq<Seq<A>>)tclass.flatM(ms);
    }

    static <T, A, B> Seq<? extends Traversable<T, B>>
    mapM(Traversable<T, A> xs, Function<? super A, ? extends $<µ, B>> f) {
        return narrow(tclass.mapM(xs, f));
    }

    @SuppressWarnings("unchecked")
    static <A, B> Seq<Seq<B>>
    mapM(Seq<A> xs, Function<? super A, ? extends $<µ, B>> f) {
        return (Seq<Seq<B>>)tclass.mapM(xs, f);
    }

    static <A> Seq<Unit> sequence(Foldable<? extends $<µ, A>> ms) {
        return narrow(tclass.sequence(ms));
    }

    static <A, B> Seq<Unit>
    mapM_(Foldable<A> xs, Function<? super A, ? extends $<µ, B>> f) {
        return narrow(tclass.mapM_(xs, f));
    }

    static <A> Seq<Seq<A>>
    filterM(Seq<A> xs, Function<? super A, ? extends $<µ, Boolean>> p) {
        return narrow(tclass.filterM(xs, p));
    }

    static <A, B> Seq<B>
    foldM(B r0, Foldable<A> xs, BiFunction<B, ? super A, ? extends $<µ, B>> f) {
        return narrow(tclass.foldM(r0, xs, f));
    }

    static <A> Seq<Seq<A>> replicateM(int n, $<µ, A> a) {
        return narrow(tclass.replicateM(n, a));
    }

    static <A> Seq<Unit> replicateM_(int n, $<µ, A> a) {
        return narrow(tclass.replicateM_(n, a));
    }

    /**
     * Returns the string representation of a sequence.
     */
    default String show() {
        return show(Integer.MAX_VALUE);
    }

    /**
     * Returns the string representation of a sequence.
     *
     * @param n number of elements to be shown
     */
    default String show(int n) {
        return show(n, ", ", "[", "]");
    }

    /**
     * Returns the string representation of a sequence.
     *
     * @param delimiter the sequence of characters to be used between each element
     * @param prefix the sequence of characters to be used at the beginning
     * @param suffix the sequence of characters to be used at the end
     */
    @Override
    default String show(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        return show(Integer.MAX_VALUE, delimiter, prefix, suffix);
    }

    /**
     * Returns the string representation of a sequence.
     *
     * @param n number of elements to be shown
     * @param delimiter the sequence of characters to be used between each element
     * @param prefix the sequence of characters to be used at the beginning
     * @param suffix the sequence of characters to be used at the end
     */
    default String show(int n, CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        StringJoiner joiner = new StringJoiner(delimiter, prefix, suffix);
        Seq<T> xs = this; int i = 0;
        for (; !xs.isEmpty() && i < n; xs = xs.tail(), i++) {
            joiner.add(String.valueOf(xs.head()));
        }
        if (!xs.isEmpty()) {
            joiner.add("...");
        }
        return joiner.toString();
    }
}
