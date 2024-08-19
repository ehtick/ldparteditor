/**
 * CSG.java
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.swt.widgets.Display;
import org.lwjgl.util.vector.Matrix4f;
import org.nschmidt.ldparteditor.composite.Composite3D;
import org.nschmidt.ldparteditor.data.DatFile;
import org.nschmidt.ldparteditor.data.GColour;
import org.nschmidt.ldparteditor.data.GData1;
import org.nschmidt.ldparteditor.data.GData3;
import org.nschmidt.ldparteditor.enumtype.LDConfig;
import org.nschmidt.ldparteditor.enumtype.View;
import org.nschmidt.ldparteditor.helper.LDPartEditorException;
import org.nschmidt.ldparteditor.helper.composite3d.GuiStatusManager;

/**
 * Constructive Solid Geometry (CSG).
 *
 * This implementation is a Java port of <a
 * href="https://github.com/evanw/csg.js/">https://github.com/evanw/csg.js/</a>
 * with some additional features like polygon extrude, transformations etc.
 * Thanks to the author for creating the CSG.js library.<br>
 * <br>
 *
 * <b>Implementation Details</b>
 *
 * All CSG operations are implemented in terms of two functions,
 * {@link Node#clipTo(org.nschmidt.csg.Node)} and {@link Node#invert()}, which
 * remove parts of a BSP tree inside another BSP tree and swap solid and empty
 * space, respectively. To find the union of {@code a} and {@code b}, we want to
 * remove everything in {@code a} inside {@code b} and everything in {@code b}
 * inside {@code a}, then combine polygons from {@code a} and {@code b} into one
 * solid:
 *
 * <blockquote>
 *
 * <pre>
 * a.clipTo(b);
 * b.clipTo(a);
 * a.build(b.allPolygons());
 * </pre>
 *
 * </blockquote>
 *
 * The only tricky part is handling overlapping coplanar polygons in both trees.
 * The code above keeps both copies, but we need to keep them in one tree and
 * remove them in the other tree. To remove them from {@code b} we can clip the
 * inverse of {@code b} against {@code a}. The code for union now looks like
 * this:
 *
 * <blockquote>
 *
 * <pre>
 * a.clipTo(b);
 * b.clipTo(a);
 * b.invert();
 * b.clipTo(a);
 * b.invert();
 * a.build(b.allPolygons());
 * </pre>
 *
 * </blockquote>
 *
 * Subtraction and intersection naturally follow from set operations. If union
 * is {@code A | B}, difference is {@code A - B = ~(~A | B)} and intersection
 * is {@code A & B =
 * ~(~A | ~B)} where {@code ~} is the complement operator.
 */
public class CSG {

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    private SortedMap<GData3, IdAndPlane> csgResult = new TreeMap<>();

    private List<Polygon> polygons;
    private Bounds bounds = null;

    private CSG() {
        globalOptimizationRate = 100.0;
    }

    /**
     * Constructs a CSG from a list of {@link Polygon} instances.
     *
     * @param polygons
     *            polygons
     * @return a CSG instance
     */
    static CSG fromPolygons(List<Polygon> polygons) {

        CSG csg = new CSG();
        csg.polygons = polygons;
        return csg;
    }

    private CSG createClone() {
        CSG csg = new CSG();

        csg.polygons = new ArrayList<>();
        for (Polygon polygon : polygons) {
            csg.polygons.add(polygon.createClone());
        }

        return csg;
    }

    /**
     *
     * @return the polygons of this CSG
     */
    public List<Polygon> getPolygons() {
        return polygons;
    }

    /**
     * Return a new CSG solid representing the union of this csg and the
     * specified csg.
     *
     * <b>Note:</b> Neither this csg nor the specified csg are modified.
     *
     * <blockquote>
     *
     * <pre>
     *    A.union(B)
     *
     *    +-------+            +-------+
     *    |       |            |       |
     *    |   A   |            |       |
     *    |    +--+----+   =   |       +----+
     *    +----+--+    |       +----+       |
     *         |   B   |            |       |
     *         |       |            |       |
     *         +-------+            +-------+
     * </pre>
     *
     * </blockquote>
     *
     *
     * @param csg
     *            other csg
     *
     * @return union of this csg and the specified csg
     */
    public CSG union(CSG csg) {

        // This is a little bit "slower", but much more accurate than the previous version!

        CompletableFuture<CSG> f1 = CompletableFuture.supplyAsync(() -> difference(csg, false));
        CompletableFuture<CSG> f2 = CompletableFuture.supplyAsync(() -> csg.difference(this, false));
        CompletableFuture.allOf(f1, f2).join();

        final CSG c1;
        final CSG c2;

        try {
            c1 = f1.get();
            c2 = f2.get();
        } catch (ExecutionException e) {
            // Exceptions should (tm) already be thrown by the "join()" call.
            throw new LDPartEditorException(e);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LDPartEditorException(ie);
        }

        final List<Polygon> unifiedPolygons = new ArrayList<>(c1.polygons.size() + c2.polygons.size());
        unifiedPolygons.addAll(c1.polygons);
        unifiedPolygons.addAll(c2.polygons);

        return CSG.fromPolygons(unifiedPolygons);
    }

    /**
     * Return a new CSG solid representing the difference of this csg and the
     * specified csg.
     *
     * <b>Note:</b> Neither this csg nor the specified csg are modified.
     *
     * <blockquote>
     *
     * <pre>
     * A.difference(B)
     *
     * +-------+            +-------+
     * |       |            |       |
     * |   A   |            |       |
     * |    +--+----+   =   |    +--+
     * +----+--+    |       +----+
     *      |   B   |
     *      |       |
     *      +-------+
     * </pre>
     *
     * </blockquote>
     *
     * @param csg
     *            other csg
     * @return difference of this csg and the specified csg
     */
    public CSG difference(CSG csg) {
        return difference(csg, true);
    }

    public CSG difference(CSG csg, boolean includeB) {

        final List<Polygon> thisPolys = this.createClone().polygons;
        final List<Polygon> otherPolys = csg.createClone().polygons;
        final Bounds thisBounds = this.getBounds();
        final Bounds otherBounds = csg.getBounds();

        final List<Polygon> nonIntersectingPolys = new ArrayList<>();

        thisPolys.removeIf(poly -> {
            final boolean result = !otherBounds.intersects(poly.getBounds());
            if (result) {
                nonIntersectingPolys.add(poly);
            }
            return result;
        });

        otherPolys.removeIf(poly ->
            !thisBounds.intersects(poly.getBounds())
        );

        CompletableFuture<Node> f1 = CompletableFuture.supplyAsync(() -> new Node(thisPolys));
        CompletableFuture<Node> f2 = CompletableFuture.supplyAsync(() -> new Node(otherPolys));
        CompletableFuture.allOf(f1, f2).join();

        final Node a;
        final Node b;

        try {
            a = f1.get();
            b = f2.get();
        } catch (ExecutionException e) {
            // Exceptions should (tm) already be thrown by the "join()" call.
            throw new LDPartEditorException(e);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LDPartEditorException(ie);
        }

        a.invert();
        a.clipTo(b);
        b.clipTo(a);
        b.invert();
        b.clipTo(a);
        b.invert();

        Deque<NodePolygon> st = new LinkedList<>();
        if (includeB) {
            st.push(new NodePolygon(a, b.allPolygons(new ArrayList<>())));
        } else {
            st.push(new NodePolygon(a, new ArrayList<>()));
        }

        while (!st.isEmpty()) {
            NodePolygon np = st.pop();
            List<NodePolygon> npr = np.node().buildForResult(np.polygons());
            for (NodePolygon np2 : npr) {
                st.push(np2);
            }
        }

        a.invert();

        final List<Polygon> resultPolys = a.allPolygons(nonIntersectingPolys);
        return CSG.fromPolygons(resultPolys);
    }

    /**
     * Return a new CSG solid representing the intersection of this csg and the
     * specified csg.
     *
     * <b>Note:</b> Neither this csg nor the specified csg are modified.
     *
     * <blockquote>
     *
     * <pre>
     *     A.intersect(B)
     *
     *     +-------+
     *     |       |
     *     |   A   |
     *     |    +--+----+   =   +--+
     *     +----+--+    |       +--+
     *          |   B   |
     *          |       |
     *          +-------+
     * }
     * </pre>
     *
     * </blockquote>
     *
     * @param csg
     *            other csg
     * @return intersection of this csg and the specified csg
     */
    public CSG intersect(CSG csg) {

        CompletableFuture<Node> f1 = CompletableFuture.supplyAsync(() -> new Node(this.createClone().polygons));
        CompletableFuture<Node> f2 = CompletableFuture.supplyAsync(() -> new Node(csg.createClone().polygons));
        CompletableFuture.allOf(f1, f2).join();

        final Node a;
        final Node b;

        try {
            a = f1.get();
            b = f2.get();
        } catch (ExecutionException e) {
            throw new LDPartEditorException(e);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LDPartEditorException(ie);
        }

        a.invert();
        b.clipTo(a);
        b.invert();
        a.clipTo(b);
        b.clipTo(a);

        Deque<NodePolygon> st = new LinkedList<>();
        st.push(new NodePolygon(a, b.allPolygons(new ArrayList<>())));
        while (!st.isEmpty()) {
            NodePolygon np = st.pop();
            List<NodePolygon> npr = np.node().buildForResult(np.polygons());
            for (NodePolygon np2 : npr) {
                st.push(np2);
            }
        }

        a.invert();
        return CSG.fromPolygons(a.allPolygons(new ArrayList<>()));
    }

    /**
     * Returns this csg as list of LDraw triangles
     *
     * @return this csg as list of LDraw triangles
     */
    private SortedMap<GData3, IdAndPlane> toLDrawTriangles(GData1 parent) {
        SortedMap<GData3, IdAndPlane> result = new TreeMap<>();
        for (Polygon p : this.polygons) {
            result.putAll(p.toLDrawTriangles(parent));
        }
        return result;
    }

    public GData1 compile() {
        Matrix4f id = new Matrix4f();
        Matrix4f.setIdentity(id);
        GColour col = LDConfig.getColour16();
        GData1 g1 = new GData1(-1, col.getR(), col.getG(), col.getB(), 1f, id, View.ACCURATE_ID, new ArrayList<>(), null, null, 1, false, id, View.ACCURATE_ID, null, View.DUMMY_REFERENCE, true, false,
                new HashSet<>(), View.DUMMY_REFERENCE);
        this.csgResult = toLDrawTriangles(g1);
        return g1;
    }

    public void draw(Composite3D c3d, DatFile df) {
        for (GData3 tri : getResult(df).keySet()) {
            tri.drawGL20(c3d);
        }
    }

    public void drawTextured(Composite3D c3d, DatFile df) {
        for (GData3 tri : getResult(df).keySet()) {
            tri.drawGL20BFCtextured(c3d);
        }
    }

    private volatile boolean shouldOptimize = true;
    private volatile SortedMap<GData3, IdAndPlane> optimizedResult = null;
    private volatile SortedMap<GData3, IdAndPlane> optimizedTriangles = new TreeMap<>();
    private final Random rnd = new Random(12345678L);
    public static volatile long timeOfLastOptimization = -1;
    public static volatile double globalOptimizationRate = 100.0;

    private volatile double optimizationTries = 1.0;
    private volatile double optimizationSuccess = 1.0;
    private volatile double failureStrike = 0;
    private volatile int tjunctionPause = 0;
    private volatile int flipPause = 0;

    private final Map<GData3, Map<GData3, Boolean>> flipCache = new HashMap<>();

    public SortedMap<GData3, IdAndPlane> getResult(DatFile df) {

        if (optimizedTriangles.isEmpty() && df != null && df.isOptimizingCSG()) {
            optimizedTriangles = new TreeMap<>();
            optimizedTriangles.putAll(csgResult);
        }

        if (shouldOptimize && df != null && df.isOptimizingCSG()) {
            final Composite3D lastC3d = DatFile.getLastHoveredComposite();
            if (lastC3d != null) {
                Display.getDefault().asyncExec(() -> GuiStatusManager.updateStatus(lastC3d));
            }

            shouldOptimize = false;
            EXECUTOR_SERVICE.execute(() -> {

                SortedMap<GData3, IdAndPlane> optimization = new TreeMap<>();
                if (optimizedResult != null) {
                    optimization.putAll(optimizedResult);
                } else {
                    // Unify once before the first optimization loop
                    optimization.putAll(CSGOptimizerUnificator.optimize(optimizedTriangles));
                }

                // Optimize for each plane
                SortedMap<Plane, List<GData3>> trianglesPerPlane = new TreeMap<>();
                List<GData3> obsoleteTriangles = new ArrayList<>();
                for (Entry<GData3, IdAndPlane> entry : optimization.entrySet()) {
                    IdAndPlane id = entry.getValue();
                    if (id == null) {
                        obsoleteTriangles.add(entry.getKey());
                        continue;
                    }
                    final Plane p = id.plane;
                    List<GData3> triangles = trianglesPerPlane.get(p);
                    if (triangles == null) {
                        triangles = new ArrayList<>();
                        triangles.add(entry.getKey());
                        trianglesPerPlane.put(p, triangles);
                    } else {
                        triangles.add(entry.getKey());
                    }
                }
                for (GData3 g : obsoleteTriangles) {
                    optimization.remove(g);
                }

                int action = rnd.nextInt(3);
                boolean foundOptimization = false;

                if (action == 0 || action == 2) {
                    if (tjunctionPause > 0) {
                        tjunctionPause--;
                        action = 2;
                    } else {
                        foundOptimization = CSGOptimizerTJunction.optimize(trianglesPerPlane, optimization);
                        if (!foundOptimization) {
                            tjunctionPause = 1000;
                        }
                    }
                }

                if (action == 1) {
                    if (flipPause > 0) {
                        flipPause--;
                        action = 2;
                    } else {
                        foundOptimization = CSGOptimizerFlipTriangle.optimize(rnd, trianglesPerPlane, optimization, flipCache);
                        if (!foundOptimization) {
                            flipPause = 1000;
                        }
                    }
                }

                if (action == 2 && tjunctionPause > 0) {
                    foundOptimization = CSGOptimizerEdgeCollapse.optimize(trianglesPerPlane, optimization);
                    if (!foundOptimization) {
                        flipPause = 0;
                    }
                }

                if (foundOptimization) {
                    optimizationSuccess++;
                    failureStrike = 0;
                } else if (optimizationSuccess > 0) {
                    optimizationSuccess--;
                    if (failureStrike < 100) {
                        failureStrike++;
                    }
                }
                optimizationTries++;

                final double rate = Math.max(1.0 - optimizationSuccess / optimizationTries, failureStrike / 100.0) * 100.0;
                if (rate < 99.0 && failureStrike < 100) {
                    globalOptimizationRate = rate;
                    timeOfLastOptimization = System.currentTimeMillis();
                }

                optimizedResult = optimization;
                shouldOptimize = true;
            });
        }

        if (optimizedResult == null) {
            return csgResult;
        } else {
            return optimizedResult;
        }
    }

    /**
     * Returns a transformed copy of this CSG.
     *
     * @param transform
     *            the transform to apply
     *
     * @return a transformed copy of this CSG
     */
    private CSG transformed(Transform transform) {
        List<Polygon> newpolygons = new ArrayList<>();
        for (Polygon p : polygons) {
            newpolygons.add(p.transformed(transform));
        }
        return CSG.fromPolygons(newpolygons);
    }

    /**
     * Returns a transformed copy of this CSG.
     *
     * @param transform
     *            the transform to apply
     * @param id
     *
     * @return a transformed copy of this CSG
     */
    private CSG transformed(Transform transform, GColour c, int id) {
        List<Polygon> newpolygons = new ArrayList<>();
        for (Polygon p : polygons) {
            newpolygons.add(p.transformed(transform, c, id));
        }
        return CSG.fromPolygons(newpolygons);
    }

    /**
     * Returns a transformed copy of this CSG.
     *
     * @param transform
     *            the transform to apply
     *
     * @return a transformed copy of this CSG
     */
    public CSG transformed(Matrix4f transform) {
        return transformed(new Transform().apply(transform));
    }

    /**
     * Returns a transformed coloured, copy of this CSG.
     *
     * @param transform
     *            the transform to apply
     * @param id
     *
     * @return a transformed copy of this CSG
     */
    public CSG transformed(Matrix4f transform, GColour c, int id) {
        return transformed(new Transform().apply(transform), c, id);
    }

    /**
     * Returns the bounds of this csg.
     *
     * @return bouds of this csg
     */
    public Bounds getBounds() {
        Bounds result = bounds;
        if (result == null) {
            if (!polygons.isEmpty()) {
                result = new Bounds();
                for (Polygon t : polygons) {
                    Bounds b = t.getBounds();
                    result.union(b);
                }
            } else {
                result = new Bounds(new VectorCSGd(0, 0, 0), new VectorCSGd(0, 0, 0));
            }
            bounds = result;
        }
        return result;
    }
}

