// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
// Copyright © 1991-2018 Unicode, Inc. All rights reserved.
// Distributed under the Terms of Use in http://www.unicode.org/copyright.html.
// Portions Copyright (c) 2018, Chris Fraire <cfraire@me.com>.
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of the Unicode data files and any associated documentation
// (the "Data Files") or Unicode software and any associated documentation
// (the "Software") to deal in the Data Files or Software
// without restriction, including without limitation the rights to use,
// copy, modify, merge, publish, distribute, and/or sell copies of
// the Data Files or Software, and to permit persons to whom the Data Files
// or Software are furnished to do so, provided that either
// (a) this copyright and permission notice appear with all copies
// of the Data Files or Software, or
// (b) this copyright and permission notice appear in associated
// Documentation.
//
// THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF
// ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
// WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT OF THIRD PARTY RIGHTS.
// IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS
// NOTICE BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL
// DAMAGES, OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
// DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
// TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
// PERFORMANCE OF THE DATA FILES OR SOFTWARE.
//
// Except as contained in this notice, the name of a copyright holder
// shall not be used in advertising or otherwise to promote the sale,
// use or other dealings in these Data Files or Software without prior
// written authorization of the copyright holder.

/*
 * ********************************************************************************
 * Copyright (C) 2007-2011, International Business Machines Corporation and others.
 * All Rights Reserved.
 * ********************************************************************************
 */
package org.opensolaris.opengrok.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * TextTrieMap is a trie implementation for supporting
 * fast prefix match for the key.
 * <p>
 * OpenGrok's import of this
 * <a href="http://source.icu-project.org/repos/icu/icu4j/tags/release-58-1/main/classes/core/src/com/ibm/icu/impl/TextTrieMap.java">
 * ICU class</a> strips out its {@code ignoreCase} handling which depends on
 * that project's per-character case-folding algorithms that are external to
 * this class. OpenGrok users requiring case-insensitive tries should build
 * with {@link String} case-folded entries and search for likewise
 * {@link String} case-folded entries.
 */
public class TextTrieMap<V> {

    private Node _root = new Node();

    /**
     * Adds the text key and its associated object in this object.
     *
     * @param text The text.
     * @param val The value object associated with the text.
     */
    public TextTrieMap<V> put(CharSequence text, V val) {
        CharIterator chitr = new CharIterator(text, 0);
        _root.add(chitr, val);
        return this;
    }

    /**
     * Gets an iterator of the objects associated with the
     * longest prefix matching string key.
     *
     * @param text The text to be matched with prefixes.
     * @return An iterator of the objects associated with
     * the longest prefix matching matching key, or null
     * if no matching entry is found.
     */
    public Iterator<V> get(String text) {
        return get(text, 0);
    }

    /**
     * Gets an iterator of the objects associated with the
     * longest prefix matching string key starting at the
     * specified position.
     *
     * @param text The text to be matched with prefixes.
     * @param start The start index of of the text
     * @return An iterator of the objects associated with the
     * longest prefix matching matching key, or null if no
     * matching entry is found.
     */
    public Iterator<V> get(CharSequence text, int start) {
        return get(text, start, null);
    }

    public Iterator<V> get(CharSequence text, int start, int[] matchLen) {
        LongestMatchHandler<V> handler = new LongestMatchHandler<V>();
        find(text, start, handler);
        if (matchLen != null && matchLen.length > 0) {
            matchLen[0] = handler.getMatchLength();
        }
        return handler.getMatches();
    }

    public void find(CharSequence text, ResultHandler<V> handler) {
        find(text, 0, handler);
    }

    public void find(CharSequence text, int offset, ResultHandler<V> handler) {
        CharIterator chitr = new CharIterator(text, offset);
        find(_root, chitr, handler);
    }

    private void find(Node node, CharIterator chitr, ResultHandler<V> handler) {
        Iterator<V> values = node.values();
        if (values != null) {
            if (!handler.handlePrefixMatch(chitr.processedLength(), values)) {
                return;
            }
        }

        Node nextMatch = node.findMatch(chitr);
        if (nextMatch != null) {
            find(nextMatch, chitr, handler);
        }
    }

    public static class CharIterator implements Iterator<Character> {
        private CharSequence _text;
        private int _nextIdx;
        private int _startIdx;

        private Character _remainingChar;

        CharIterator(CharSequence text, int offset) {
            _text = text;
            _nextIdx = _startIdx = offset;
        }

        /* (non-Javadoc)
         * @see java.util.Iterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            if (_nextIdx == _text.length() && _remainingChar == null) {
                return false;
            }
            return true;
        }

        /* (non-Javadoc)
         * @see java.util.Iterator#next()
         */
        @Override
        public Character next() {
            if (_nextIdx == _text.length() && _remainingChar == null) {
                return null;
            }
            Character next;
            if (_remainingChar != null) {
                next = _remainingChar;
                _remainingChar = null;
            } else {
                next = _text.charAt(_nextIdx);
                _nextIdx++;
            }
            return next;
        }

        /* (non-Javadoc)
         * @see java.util.Iterator#remove()
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove() not supproted");
        }

        public int nextIndex() {
            return _nextIdx;
        }

        public int processedLength() {
            if (_remainingChar != null) {
                throw new IllegalStateException("In the middle of surrogate pair");
            }
            return _nextIdx - _startIdx;
        }
    }

    /**
     * Callback handler for processing prefix matches used by
     * find method.
     */
    public interface ResultHandler<V> {
        /**
         * Handles a prefix key match
         *
         * @param matchLength Matched key's length
         * @param values An iterator of the objects associated with the matched key
         * @return Return true to continue the search in the trie, false to quit.
         */
        public boolean handlePrefixMatch(int matchLength, Iterator<V> values);
    }

    private static class LongestMatchHandler<V> implements ResultHandler<V> {
        private Iterator<V> matches = null;
        private int length = 0;

        @Override
        public boolean handlePrefixMatch(int matchLength, Iterator<V> values) {
            if (matchLength > length) {
                length = matchLength;
                matches = values;
            }
            return true;
        }

        public Iterator<V> getMatches() {
            return matches;
        }

        public int getMatchLength() {
            return length;
        }
    }

    /**
     * Inner class representing a text node in the trie.
     */
    private class Node {
        private char[] _text;
        private List<V> _values;
        private List<Node> _children;

        private Node() {
        }

        private Node(char[] text, List<V> values, List<Node> children) {
            _text = text;
            _values = values;
            _children = children;
        }

        public Iterator<V> values() {
            if (_values == null) {
                return null;
            }
            return _values.iterator();
        }

        public void add(CharIterator chitr, V value) {
            StringBuilder buf = new StringBuilder();
            while (chitr.hasNext()) {
                buf.append(chitr.next());
            }
            add(toCharArray(buf), 0, value);
        }

        public Node findMatch(CharIterator chitr) {
            if (_children == null) {
                return null;
            }
            if (!chitr.hasNext()) {
                return null;
            }
            Node match = null;
            Character ch = chitr.next();
            for (Node child : _children) {
                if (ch < child._text[0]) {
                    break;
                }
                if (ch == child._text[0]) {
                    if (child.matchFollowing(chitr)) {
                        match = child;
                    }
                    break;
                }
            }
            return match;
        }

        private void add(char[] text, int offset, V value) {
            if (text.length == offset) {
                _values = addValue(_values, value);
                return;
            }

            if (_children == null) {
                _children = new LinkedList<Node>();
                Node child = new Node(subArray(text, offset), addValue(null, value), null);
                _children.add(child);
                return;
            }

            // walk through children
            ListIterator<Node> litr = _children.listIterator();
            while (litr.hasNext()) {
                Node next = litr.next();
                if (text[offset] < next._text[0]) {
                    litr.previous();
                    break;
                }
                if (text[offset] == next._text[0]) {
                    int matchLen = next.lenMatches(text, offset);
                    if (matchLen == next._text.length) {
                        // full match
                        next.add(text, offset + matchLen, value);
                    } else {
                        // partial match, create a branch
                        next.split(matchLen);
                        next.add(text, offset + matchLen, value);
                    }
                    return;
                }
            }
            // add a new child to this node
            litr.add(new Node(subArray(text, offset), addValue(null, value), null));
        }

        private boolean matchFollowing(CharIterator chitr) {
            boolean matched = true;
            int idx = 1;
            while (idx < _text.length) {
                if(!chitr.hasNext()) {
                    matched = false;
                    break;
                }
                Character ch = chitr.next();
                if (ch != _text[idx]) {
                    matched = false;
                    break;
                }
                idx++;
            }
            return matched;
        }

        private int lenMatches(char[] text, int offset) {
            int textLen = text.length - offset;
            int limit = _text.length < textLen ? _text.length : textLen;
            int len = 0;
            while (len < limit) {
                if (_text[len] != text[offset + len]) {
                    break;
                }
                len++;
            }
            return len;
        }

        private void split(int offset) {
            // split the current node at the offset
            char[] childText = subArray(_text, offset);
            _text = subArray(_text, 0, offset);

            // add the Node representing after the offset as a child
            Node child = new Node(childText, _values, _children);
            _values = null;

            _children = new LinkedList<Node>();
            _children.add(child);
        }

        private List<V> addValue(List<V> list, V value) {
            if (list == null) {
                list = new LinkedList<V>();
            }
            list.add(value);
            return list;
        }
    }

    private static char[] toCharArray(CharSequence text) {
        char[] array = new char[text.length()];
        for (int i = 0; i < array.length; i++) {
            array[i] = text.charAt(i);
        }
        return array;
    }

    private static char[] subArray(char[] array, int start) {
        if (start == 0) {
            return array;
        }
        char[] sub = new char[array.length - start];
        System.arraycopy(array, start, sub, 0, sub.length);
        return sub;
    }

    private static char[] subArray(char[] array, int start, int limit) {
        if (start == 0 && limit == array.length) {
            return array;
        }
        char[] sub = new char[limit - start];
        System.arraycopy(array, start, sub, 0, limit - start);
        return sub;
    }
}