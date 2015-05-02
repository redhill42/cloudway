/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.fp.parser;

/**
 * The {@code Message} object represents parse error messages. There are
 * four kinds of messages:
 *
 * <p>The fine distinction between different kinds of parse errors allows
 * the system to generate quite good error messages for the user. It
 * also allows error messages that are formatted in different languages.
 * Each kind of messages is generated by different combinators:</p>
 *
 * <ul>
 * <li>A {@code SysUnExpect} message is automatically generated by the
 *     'satisfy' combinator. The argument is the unexpected input.</li>
 * <li>A {@code UnExpect} message is generated by the 'unexpected'
 *     combinator. The argument describes the unexpected item.</li>
 * <li>A {@code Expect} message is generated by the 'label' combinator.
 *     The argument describes the expected item.</li>
 * <li>A {@code Fail} message is generated by the 'fail' combinator.
 *     The argument is some general parser message.</li>
 * </ul>
 */
public abstract class Message implements Comparable<Message> {
    private final String message;

    protected Message(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    protected abstract int ord();

    @Override
    public int compareTo(Message o) {
        return ord() - o.ord();
    }

    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (this.getClass() != obj.getClass())
            return false;
        return message.equals(((Message)obj).message);
    }

    public int hashCode() {
        return 31 * getClass().getSimpleName().hashCode() + message.hashCode();
    }

    public String toString() {
        return getClass().getSimpleName() + " " + message;
    }

    public static final class SysUnExpect extends Message {
        public SysUnExpect(String message) {
            super(message);
        }

        @Override
        protected int ord() {
            return 1;
        }
    }

    public static final class UnExpect extends Message {
        public UnExpect(String message) {
            super(message);
        }

        @Override
        protected int ord() {
            return 2;
        }
    }

    public static final class Expect extends Message {
        public Expect(String message) {
            super(message);
        }

        @Override
        protected int ord() {
            return 3;
        }
    }

    public static final class Fail extends Message {
        public Fail(String message) {
            super(message);
        }

        @Override
        protected int ord() {
            return 4;
        }
    }
}