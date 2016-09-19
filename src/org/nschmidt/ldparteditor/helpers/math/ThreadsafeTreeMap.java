/* MIT - License

Copyright (c) 2012 - this year, Nils Schmidt

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. */
package org.nschmidt.ldparteditor.helpers.math;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author nils
 *
 */
public class ThreadsafeTreeMap<K, V> implements Map<K, V> {

    private final TreeMap<K, V> map;
    private final ReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock rl = rwl.readLock();
    private final Lock wl = rwl.writeLock();

    public ThreadsafeTreeMap() {
        wl.lock();
        map = new TreeMap<K, V>();
        wl.unlock();
    }

    public ThreadsafeTreeMap(Map<? extends K, ? extends V> m) {
        wl.lock();
        map = new TreeMap<K, V>(m);
        wl.unlock();
    }

    @Override
    public void clear() {
        wl.lock();
        map.clear();
        wl.unlock();
    }

    @Override
    public Object clone() {
        rl.lock();
        final Object obj = map.clone();
        rl.unlock();
        return obj;
    }

    @Override
    public boolean containsKey(Object key) {
        rl.lock();
        final boolean value = map.containsKey(key);
        rl.unlock();
        return value;
    }

    @Override
    public boolean containsValue(Object value) {
        rl.lock();
        final boolean rvalue = map.containsValue(value);
        rl.unlock();
        return rvalue;
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        rl.lock();
        final Set<java.util.Map.Entry<K, V>> rvalue = map.entrySet();
        rl.unlock();
        return rvalue;
    }
    
    public Set<java.util.Map.Entry<K, V>> threadSafeEntrySet() {
        rl.lock();
        final Set<java.util.Map.Entry<K, V>> val = map.entrySet();
        final Set<java.util.Map.Entry<K, V>> result = new HashSet<>();
        for (Entry<K, V> entry : val) {
            result.add(entry);
        }
        rl.unlock();
        return result;
    }

    @Override
    public V get(Object key) {
        rl.lock();
        final V val = map.get(key);
        rl.unlock();
        return val;
    }

    @Override
    public boolean isEmpty() {
        rl.lock();
        final boolean rvalue = map.isEmpty();
        rl.unlock();
        return rvalue;
    }

    @Override
    public Set<K> keySet() {
        rl.lock();
        final Set<K> val = map.keySet();
        rl.unlock();
        return val;
    }
    
    public List<K> threadSafeKeyList() {
        rl.lock();
        final Set<K> val = map.keySet();
        final ArrayList<K> result = new ArrayList<>();
        for (K key : val) {
            result.add(key);
        }
        rl.unlock();
        return result;
    }

    @Override
    public V put(K key, V value) {
        wl.lock();
        final V val = map.put(key, value);
        wl.unlock();
        return val;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        wl.lock();
        map.putAll(m);
        wl.unlock();
    }

    @Override
    public V remove(Object key) {
        wl.lock();
        final V val = map.remove(key);
        wl.unlock();
        return val;
    }

    @Override
    public int size() {
        rl.lock();
        final int rvalue = map.size();
        rl.unlock();
        return rvalue;
    }

    public K firstKey() {
        rl.lock();
        final K val = map.firstKey();
        rl.unlock();
        return val;
    }

    public K lastKey() {
        rl.lock();
        final K val = map.lastKey();
        rl.unlock();
        return val;
    }

    @Override
    public Collection<V> values() {
        rl.lock();
        final Collection<V> rvalue = map.values();
        rl.unlock();
        return rvalue;
    }
}
