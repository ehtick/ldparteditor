/**
 * Node.java
 *
 * Copyright 2014-2014 Michael Hoffer <info@michaelhoffer.de>. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY Michael Hoffer <info@michaelhoffer.de> "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL Michael Hoffer <info@michaelhoffer.de> OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of Michael Hoffer
 * <info@michaelhoffer.de>.
 */
package org.nschmidt.csg;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.nschmidt.ldparteditor.helper.LDPartEditorException;
import org.nschmidt.ldparteditor.logger.NLogger;

/**
 * Holds a node in a BSP tree. A BSP tree is built from a collection of polygons
 * by picking a polygon to split along. That polygon (and all other coplanar
 * polygons) are added directly to that node and the other polygons are added to
 * the front and/or back subtrees. This is not a leafy BSP tree since there is
 * no distinction between internal and leaf nodes.
 */
final class Node {

    /**
     * Polygons.
     */
    private List<Polygon> polygons;
    /**
     * Plane used for BSP.
     */
    private Plane plane;
    /**
     * Polygons in front of the plane.
     */
    private Node front;
    /**
     * Polygons in back of the plane.
     */
    private Node back;

    /**
     * Constructor.
     *
     * Creates a BSP node consisting of the specified polygons.
     *
     * @param polygons
     *            polygons
     */
    public Node(List<Polygon> polygons) {
        this.polygons = new ArrayList<>();
        if (polygons != null) {
            Deque<NodePolygon> st = new LinkedList<>();
            st.push(new NodePolygon(this, polygons));
            int it = 0;
            while (!st.isEmpty() && it < 10000) {
                it++;
                NodePolygon np = st.pop();
                List<NodePolygon> npr = np.node().build(np.polygons());
                for (NodePolygon np2 : npr) {
                    st.push(np2);
                }
            }

            if (it == 10000) {
                NLogger.debug(getClass(), "CSG limit exceeded."); //$NON-NLS-1$
            }
        }
    }

    /**
     * Constructor. Creates a node without polygons.
     */
    public Node() {
        this.polygons = new ArrayList<>();
    }

    /**
     * Converts solid space to empty space and vice verca.
     */
    public void invert() {
        final Deque<Node> st = new LinkedList<>();
        st.push(this);
        while (!st.isEmpty()) {
            final Node n = st.pop();
            final List<Polygon> polys = n.polygons;
            if (n.plane == null) {
                if (polys.isEmpty()) {
                    continue;
                }

                n.plane = polys.get(0).plane.createClone();
            }

            for (Polygon polygon : polys) {
                polygon.flip();
            }

            n.plane.flip();

            if (n.back != null) {
                st.push(n.back);
            }
            if (n.front != null) {
                st.push(n.front);
            }
            Node temp = n.front;
            n.front = n.back;
            n.back = temp;
        }
    }

    /**
     * Removes all polygons in the {@link polygons} list that are
     * contained within this BSP tree.
     *
     * <b>Note:</b> polygons are splitted if necessary.
     *
     * @param polygonsToClip
     *            the polygons to clip
     *
     * @return the cliped list of polygons
     */

    private List<Polygon> clipPolygons(List<Polygon> polygonsToClip) {

        if (this.plane == null) {
            return new ArrayList<>(polygonsToClip);
        }

        final Deque<NodeArgs> st = new LinkedList<>();
        st.push(new NodeArgs(this, polygonsToClip, Side.NONE, null));

        NodeArgs lastArgs = null;
        while (!st.isEmpty()) {
            final NodeArgs a = st.pop();
            if (a.returning) {
                a.returning = false;
                a.frontP.addAll(a.backP);

                if (a.parent != null) {
                    if (a.side == Side.FRONT) {
                        a.parent.frontP = a.frontP;
                    } else {
                        a.parent.backP = a.frontP;
                    }
                }
                lastArgs = a;
            } else {
                final Node n = a.node;
                if (n.plane == null) {
                    continue;
                }
                a.returning = true;
                st.push(a);

                // Speed up with parallelism
                List<int[]> types = a.polygons
                        .stream()
                        .parallel()
                        .map(poly ->
                        n.plane.getTypes(poly))
                        .toList();

                int i = 0;
                for (Polygon polygon : a.polygons) {
                    n.plane.splitPolygonForClip(polygon, types.get(i), a.frontP, a.backP);
                    i++;
                }

                if (n.back != null) {
                    st.push(new NodeArgs(n.back, a.backP, Side.BACK, a)); // returns a.backP
                } else {
                    a.backP = new ArrayList<>(0);
                }
                if (n.front != null) {
                    st.push(new NodeArgs(n.front, a.frontP, Side.FRONT, a)); // returns a.frontP
                }
            }
        }

        if (lastArgs != null) {
            return lastArgs.frontP;
        } else {
            return new ArrayList<>(polygonsToClip);
        }
    }

    private enum Side {
        FRONT, BACK, NONE
    }

    private class NodeArgs {
        private Side side;
        private NodeArgs parent;
        private List<Polygon> polygons;
        private List<Polygon> frontP = new ArrayList<>();
        private List<Polygon> backP = new ArrayList<>();
        private Node node;
        private boolean returning = false;
        private NodeArgs(Node n, List<Polygon> polys, Side s, NodeArgs p) {
            parent = p;
            side = s;
            node = n;
            polygons = polys;
        }
    }

    /**
     * Removes all polygons in this BSP tree that are inside the specified BSP
     * tree ({@code bsp}).
     *
     * <b>Note:</b> polygons are splitted if necessary.
     *
     * @param bsp
     *            bsp that shall be used for clipping
     */
    public void clipTo(final Node bsp) {
        final Deque<Node> st = new LinkedList<>();
        st.push(this);
        while (!st.isEmpty()) {
            final Node n = st.pop();
            if (!st.isEmpty()) {
                final Node n2 = st.pop();
                if (!st.isEmpty()) {
                    final Node n3 = st.pop();

                    CompletableFuture<List<Polygon>> f1 = CompletableFuture.supplyAsync(() -> bsp.clipPolygons(n.polygons));
                    CompletableFuture<List<Polygon>> f2 = CompletableFuture.supplyAsync(() -> bsp.clipPolygons(n2.polygons));
                    CompletableFuture<List<Polygon>> f3 = CompletableFuture.supplyAsync(() -> bsp.clipPolygons(n3.polygons));
                    CompletableFuture.allOf(f1, f2, f3).join();

                    try {
                        n.polygons = f1.get();
                        n2.polygons = f2.get();
                        n3.polygons = f3.get();
                    } catch (ExecutionException e) {
                        NLogger.error(getClass(), e);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LDPartEditorException(ie);
                    }

                    if (n3.back != null) {
                        st.push(n3.back);
                    }
                    if (n3.front != null) {
                        st.push(n3.front);
                    }
                    if (n2.back != null) {
                        st.push(n2.back);
                    }
                    if (n2.front != null) {
                        st.push(n2.front);
                    }
                    if (n.back != null) {
                        st.push(n.back);
                    }
                    if (n.front != null) {
                        st.push(n.front);
                    }

                } else {

                    CompletableFuture<List<Polygon>> f1 = CompletableFuture.supplyAsync(() -> bsp.clipPolygons(n.polygons));
                    CompletableFuture<List<Polygon>> f2 = CompletableFuture.supplyAsync(() -> bsp.clipPolygons(n2.polygons));
                    CompletableFuture.allOf(f1, f2).join();

                    try {
                        n.polygons = f1.get();
                        n2.polygons = f2.get();
                    } catch (ExecutionException e) {
                        NLogger.error(getClass(), e);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LDPartEditorException(ie);
                    }

                    if (n2.back != null) {
                        st.push(n2.back);
                    }
                    if (n2.front != null) {
                        st.push(n2.front);
                    }
                    if (n.back != null) {
                        st.push(n.back);
                    }
                    if (n.front != null) {
                        st.push(n.front);
                    }
                }
            } else {
                n.polygons = bsp.clipPolygons(n.polygons);
                if (n.back != null) {
                    st.push(n.back);
                }
                if (n.front != null) {
                    st.push(n.front);
                }
            }
        }
    }

    /**
     * Returns a list of all polygons in this BSP tree.
     *
     * @return a list of all polygons in this BSP tree
     */
    public List<Polygon> allPolygons(List<Polygon> result) {
        final Deque<Node> st = new LinkedList<>();
        st.push(this);
        while (!st.isEmpty()) {
            final Node n = st.pop();
            result.addAll(n.polygons);
            if (n.front != null) {
                st.push(n.front);
            }
            if (n.back != null) {
                st.push(n.back);
            }
        }
        return result;
    }

    /**
     * Build a BSP tree out of {@code polygons}. When called on an existing
     * tree, the new polygons are filtered down to the bottom of the tree and
     * become new nodes there. Each set of polygons is partitioned using the
     * first polygon (no heuristic is used to pick a good split).
     *
     * @param polygons
     *            polygons used to build the BSP
     */
    private final List<NodePolygon> build(List<Polygon> polygons) {

        final List<NodePolygon> result = new ArrayList<>(2);

        if (this.plane == null) {
            if (polygons.isEmpty()) {
                return result;
            }

            this.plane = polygons.get(0).plane.createClone();
        }

        List<Polygon> frontP = new ArrayList<>();
        List<Polygon> backP = new ArrayList<>();

        // Speed up with parallelism
        List<int[]> types = polygons
                .stream()
                .parallel()
                .map(poly ->
                this.plane.getTypes(poly))
                .toList();

        // parallel version does not work here
        int i = 0;
        for (Polygon polygon : polygons) {
            this.plane.splitPolygonForBuild(polygon, types.get(i), this.polygons, frontP, backP);
            i++;
        }

        // Back before front. Reversed because of the new Stack to avoid recursion stack overflows

        if (!backP.isEmpty()) {
            if (this.back == null) {
                this.back = new Node();
            }
            result.add(new NodePolygon(back, backP));
        }

        if (!frontP.isEmpty()) {
            if (this.front == null) {
                this.front = new Node();
            }
            result.add(0, new NodePolygon(front, frontP));
        }

        return result;
    }

    public final List<NodePolygon> buildForResult(List<Polygon> polygons) {

        final List<NodePolygon> result = new ArrayList<>(2);

        if (this.plane == null && !polygons.isEmpty()) {
            this.plane = polygons.get(0).plane.createClone();
        } else if (this.plane == null && polygons.isEmpty()) {
            return result;
        }

        List<Polygon> frontP = new ArrayList<>();
        List<Polygon> backP = new ArrayList<>();

        // Speed up with parallelism
        List<int[]> types = polygons
                .stream()
                .parallel()
                .map(poly ->
                this.plane.getTypes(poly))
                .toList();

        int i = 0;
        for (Polygon polygon : polygons) {
            final int[] types1 = types.get(i);
            switch (types1[types1.length - 1]) {
            case Plane.COPLANAR:
                this.polygons.add(polygon);
                break;
            case Plane.FRONT:
                frontP.add(polygon);
                break;
            case Plane.BACK:
                backP.add(polygon);
                break;
            case Plane.SPANNING:
                break;
            default:
                break;
            }
            i++;
        }

        // Back before front. Reversed because of the new Stack to avoid recursion stack overflows

        if (!backP.isEmpty()) {
            if (this.back == null) {
                this.back = new Node();
            }
            result.add(new NodePolygon(back, backP));
        }

        if (!frontP.isEmpty()) {
            if (this.front == null) {
                this.front = new Node();
            }
            result.add(0, new NodePolygon(front, frontP));
        }

        return result;
    }
}
