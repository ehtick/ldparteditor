/*
 * Copyright (c) 2007 by L. Paul Chew.
 *
 * Permission is hereby granted, without written agreement and without
 * license or royalty fees, to use, copy, modify, and distribute this
 * software and its documentation for any purpose, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package org.nschmidt.delaunay;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * An ArrayList implementation of Set. An ArraySet is good for small sets; it
 * has less overhead than a HashSet or a TreeSet.
 *
 *         Modified, optimized and customised by Nils Schmidt
 *
 *         Created December 2007. For use with Voronoi/Delaunay applet.
 *
 */
class ArraySet<E> extends AbstractSet<E> {

    private List<E> items; // Items of the set

    /**
     * Create an empty set (default initial capacity is 3).
     */
    public ArraySet() {
        this(3);
    }

    /**
     * Create an empty set with the specified initial capacity.
     *
     * @param initialCapacity
     *            the initial capacity
     */
    private ArraySet(int initialCapacity) {
        items = new ArrayList<>(initialCapacity);
    }

    /**
     * Create a set containing the items of the collection. Any duplicate items
     * are discarded.
     *
     * @param collection
     *            the source for the items of the small set
     */
    ArraySet(Collection<? extends E> collection) {
        items = new ArrayList<>(collection.size());
        for (E item : collection)
            if (!items.contains(item))
                items.add(item);
    }

    /**
     * Get the item at the specified index.
     *
     * @param index
     *            where the item is located in the ListSet
     * @return the item at the specified index
     * @throws IndexOutOfBoundsException
     *             if the index is out of bounds
     */
    public E get(int index) throws IndexOutOfBoundsException {
        return items.get(index);
    }

    @Override
    public boolean add(E item) {
        if (items.contains(item))
            return false;
        return items.add(item);
    }

    @Override
    public Iterator<E> iterator() {
        return items.iterator();
    }

    @Override
    public int size() {
        return items.size();
    }

}
