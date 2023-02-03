/* ===========================================================
 * JFreeChart : a free chart library for the Java(tm) platform
 * ===========================================================
 *
 * (C) Copyright 2000-2009, by Object Refinery Limited and Contributors.
 *
 * Project Info:  http://www.jfree.org/jfreechart/index.html
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * [Java is a trademark or registered trademark of Sun Microsystems, Inc.
 * in the United States and other countries.]
 *
 * ---------------------------
 * StackedXYAreaRenderer2.java
 * ---------------------------
 * (C) Copyright 2004-2009, by Object Refinery Limited and Contributors.
 *
 * Original Author:  David Gilbert (for Object Refinery Limited), based on
 *                   the StackedXYAreaRenderer class by Richard Atkinson;
 * Contributor(s):   -;
 *
 * Changes:
 * --------
 * 30-Apr-2004 : Version 1 (DG);
 * 15-Jul-2004 : Switched getX() with getXValue() and getY() with
 *               getYValue() (DG);
 * 10-Sep-2004 : Removed getRangeType() method (DG);
 * 06-Jan-2004 : Renamed getRangeExtent() --> findRangeBounds (DG);
 * 28-Mar-2005 : Use getXValue() and getYValue() from dataset (DG);
 * 03-Oct-2005 : Add entity generation to drawItem() method (DG);
 * ------------- JFREECHART 1.0.x ---------------------------------------------
 * 22-Aug-2006 : Handle null and empty datasets correctly in the
 *               findRangeBounds() method (DG);
 * 22-Sep-2006 : Added a flag to allow rounding of x-coordinates (after
 *               translation to Java2D space) in order to avoid the striping
 *               that can result from anti-aliasing (thanks to Doug
 *               Clayton) (DG);
 * 30-Nov-2006 : Added accessor methods for the roundXCoordinates flag (DG);
 * 20-Jun-2007 : Removed JCommon dependencies (DG);
 * 02-Jun-2008 : Fixed bug with PlotOrientation.HORIZONTAL (DG);
 *
 */

package org.jfree.chart.renderer.xy;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.entity.EntityCollection;
import org.jfree.chart.event.RendererChangeEvent;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.urls.XYURLGenerator;
import org.jfree.chart.util.PublicCloneable;
import org.jfree.chart.util.RectangleEdge;
import org.jfree.data.Range;
import org.jfree.data.xy.TableXYDataset;
import org.jfree.data.xy.XYDataset;

/**
 * A stacked area renderer for the {@link XYPlot} class.
 * The example shown here is generated by the
 * <code>StackedXYAreaChartDemo2.java</code> program included in the
 * JFreeChart demo collection:
 * <br><br>
 * <img src="../../../../../images/StackedXYAreaRenderer2Sample.png"
 * alt="StackedXYAreaRenderer2Sample.png" />
 */
public class StackedXYAreaRenderer2 extends XYAreaRenderer2
        implements Cloneable, PublicCloneable, Serializable {

    /** For serialization. */
    private static final long serialVersionUID = 7752676509764539182L;

    /**
     * This flag controls whether or not the x-coordinates (in Java2D space)
     * are rounded to integers.  When set to true, this can avoid the vertical
     * striping that anti-aliasing can generate.  However, the rounding may not
     * be appropriate for output in high resolution formats (for example,
     * vector graphics formats such as SVG and PDF).
     *
     * @since 1.0.3
     */
    private boolean roundXCoordinates;

    /**
     * Creates a new renderer.
     */
    public StackedXYAreaRenderer2() {
        this(null, null);
    }

    /**
     * Constructs a new renderer.
     *
     * @param labelGenerator  the tool tip generator to use.  <code>null</code>
     *                        is none.
     * @param urlGenerator  the URL generator (<code>null</code> permitted).
     */
    public StackedXYAreaRenderer2(XYToolTipGenerator labelGenerator,
                                  XYURLGenerator urlGenerator) {
        super(labelGenerator, urlGenerator);
        this.roundXCoordinates = true;
    }

    /**
     * Returns the flag that controls whether or not the x-coordinates (in
     * Java2D space) are rounded to integer values.
     *
     * @return The flag.
     *
     * @since 1.0.4
     *
     * @see #setRoundXCoordinates(boolean)
     */
    public boolean getRoundXCoordinates() {
        return this.roundXCoordinates;
    }

    /**
     * Sets the flag that controls whether or not the x-coordinates (in
     * Java2D space) are rounded to integer values, and sends a
     * {@link RendererChangeEvent} to all registered listeners.
     *
     * @param round  the new flag value.
     *
     * @since 1.0.4
     *
     * @see #getRoundXCoordinates()
     */
    public void setRoundXCoordinates(boolean round) {
        this.roundXCoordinates = round;
        fireChangeEvent();
    }

    /**
     * Returns the range of values the renderer requires to display all the
     * items from the specified dataset.
     *
     * @param dataset  the dataset (<code>null</code> permitted).
     *
     * @return The range (or <code>null</code> if the dataset is
     *         <code>null</code> or empty).
     */
    public Range findRangeBounds(XYDataset dataset) {
        if (dataset == null) {
            return null;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        TableXYDataset d = (TableXYDataset) dataset;
        int itemCount = d.getItemCount();
        for (int i = 0; i < itemCount; i++) {
            double[] stackValues = getStackValues((TableXYDataset) dataset,
                    d.getSeriesCount(), i);
            min = Math.min(min, stackValues[0]);
            max = Math.max(max, stackValues[1]);
        }
        if (min == Double.POSITIVE_INFINITY) {
            return null;
        }
        return new Range(min, max);
    }

    /**
     * Returns the number of passes required by the renderer.
     *
     * @return 1.
     */
    public int getPassCount() {
        return 1;
    }

    /**
     * Draws the visual representation of a single data item.
     *
     * @param g2  the graphics device.
     * @param state  the renderer state.
     * @param dataArea  the area within which the data is being drawn.
     * @param plot  the plot (can be used to obtain standard color information
     *              etc).
     * @param domainAxis  the domain axis.
     * @param rangeAxis  the range axis.
     * @param dataset  the dataset.
     * @param series  the series index (zero-based).
     * @param item  the item index (zero-based).
     * @param pass  the pass index.
     */
    public void drawItem(Graphics2D g2, XYItemRendererState state,
            Rectangle2D dataArea, XYPlot plot, ValueAxis domainAxis,
            ValueAxis rangeAxis, XYDataset dataset, int series, int item,
            boolean selected, int pass) {

        // setup for collecting optional entity info...
        Shape entityArea = null;
        EntityCollection entities = null;
        if (state.getInfo() != null) {
            entities = state.getInfo().getOwner().getEntityCollection();
        }

        TableXYDataset tdataset = (TableXYDataset) dataset;
        PlotOrientation orientation = plot.getOrientation();

        // get the data point...
        double x1 = dataset.getXValue(series, item);
        double y1 = dataset.getYValue(series, item);
        if (Double.isNaN(y1)) {
            y1 = 0.0;
        }
        double[] stack1 = getStackValues(tdataset, series, item);

        // get the previous point and the next point so we can calculate a
        // "hot spot" for the area (used by the chart entity)...
        double x0 = dataset.getXValue(series, Math.max(item - 1, 0));
        double y0 = dataset.getYValue(series, Math.max(item - 1, 0));
        if (Double.isNaN(y0)) {
            y0 = 0.0;
        }
        double[] stack0 = getStackValues(tdataset, series, Math.max(item - 1,
                0));

        int itemCount = dataset.getItemCount(series);
        int var_3072 = Math.min(item + 1,
                itemCount - 1);
		double x2 = dataset.getXValue(series, var_3072);
        double y2 = dataset.getYValue(series, Math.min(item + 1,
                itemCount - 1));
        if (Double.isNaN(y2)) {
            y2 = 0.0;
        }
        double[] stack2 = getStackValues(tdataset, series, Math.min(item + 1,
                itemCount - 1));

        double xleft = (x0 + x1) / 2.0;
        double xright = (x1 + x2) / 2.0;
        double[] stackLeft = averageStackValues(stack0, stack1);
        double[] stackRight = averageStackValues(stack1, stack2);
        double[] adjStackLeft = adjustedStackValues(stack0, stack1);
        double[] adjStackRight = adjustedStackValues(stack1, stack2);

        RectangleEdge edge0 = plot.getDomainAxisEdge();

        float transX1 = (float) domainAxis.valueToJava2D(x1, dataArea, edge0);
        float transXLeft = (float) domainAxis.valueToJava2D(xleft, dataArea,
                edge0);
        float transXRight = (float) domainAxis.valueToJava2D(xright, dataArea,
                edge0);

        if (this.roundXCoordinates) {
            transX1 = Math.round(transX1);
            transXLeft = Math.round(transXLeft);
            transXRight = Math.round(transXRight);
        }
        float transY1;

        RectangleEdge edge1 = plot.getRangeAxisEdge();

        GeneralPath left = new GeneralPath();
        GeneralPath right = new GeneralPath();
        if (y1 >= 0.0) {  // handle positive value
            transY1 = (float) rangeAxis.valueToJava2D(y1 + stack1[1], dataArea,
                    edge1);
            float transStack1 = (float) rangeAxis.valueToJava2D(stack1[1],
                    dataArea, edge1);
            float transStackLeft = (float) rangeAxis.valueToJava2D(
                    adjStackLeft[1], dataArea, edge1);

            // LEFT POLYGON
            if (y0 >= 0.0) {
                double yleft = (y0 + y1) / 2.0 + stackLeft[1];
                float transYLeft
                    = (float) rangeAxis.valueToJava2D(yleft, dataArea, edge1);
                if (orientation == PlotOrientation.VERTICAL) {
                    left.moveTo(transX1, transY1);
                    left.lineTo(transX1, transStack1);
                    left.lineTo(transXLeft, transStackLeft);
                    left.lineTo(transXLeft, transYLeft);
                }
                else {
                    left.moveTo(transY1, transX1);
                    left.lineTo(transStack1, transX1);
                    left.lineTo(transStackLeft, transXLeft);
                    left.lineTo(transYLeft, transXLeft);
                }
                left.closePath();
            }
            else {
                if (orientation == PlotOrientation.VERTICAL) {
                    left.moveTo(transX1, transStack1);
                    left.lineTo(transX1, transY1);
                    left.lineTo(transXLeft, transStackLeft);
                }
                else {
                    left.moveTo(transStack1, transX1);
                    left.lineTo(transY1, transX1);
                    left.lineTo(transStackLeft, transXLeft);
                }
                left.closePath();
            }

            float transStackRight = (float) rangeAxis.valueToJava2D(
                    adjStackRight[1], dataArea, edge1);
            // RIGHT POLYGON
            if (y2 >= 0.0) {
                double yright = (y1 + y2) / 2.0 + stackRight[1];
                float transYRight
                    = (float) rangeAxis.valueToJava2D(yright, dataArea, edge1);
                if (orientation == PlotOrientation.VERTICAL) {
                    right.moveTo(transX1, transStack1);
                    right.lineTo(transX1, transY1);
                    right.lineTo(transXRight, transYRight);
                    right.lineTo(transXRight, transStackRight);
                }
                else {
                    right.moveTo(transStack1, transX1);
                    right.lineTo(transY1, transX1);
                    right.lineTo(transYRight, transXRight);
                    right.lineTo(transStackRight, transXRight);
                }
                right.closePath();
            }
            else {
                if (orientation == PlotOrientation.VERTICAL) {
                    right.moveTo(transX1, transStack1);
                    right.lineTo(transX1, transY1);
                    right.lineTo(transXRight, transStackRight);
                }
                else {
                    right.moveTo(transStack1, transX1);
                    right.lineTo(transY1, transX1);
                    right.lineTo(transStackRight, transXRight);
                }
                right.closePath();
            }
        }
        else {  // handle negative value
            transY1 = (float) rangeAxis.valueToJava2D(y1 + stack1[0], dataArea,
                    edge1);
            float transStack1 = (float) rangeAxis.valueToJava2D(stack1[0],
                    dataArea, edge1);
            float transStackLeft = (float) rangeAxis.valueToJava2D(
                    adjStackLeft[0], dataArea, edge1);

            // LEFT POLYGON
            if (y0 >= 0.0) {
                if (orientation == PlotOrientation.VERTICAL) {
                    left.moveTo(transX1, transStack1);
                    left.lineTo(transX1, transY1);
                    left.lineTo(transXLeft, transStackLeft);
                }
                else {
                    left.moveTo(transStack1, transX1);
                    left.lineTo(transY1, transX1);
                    left.lineTo(transStackLeft, transXLeft);
                }
                left.clone();
            }
            else {
                double yleft = (y0 + y1) / 2.0 + stackLeft[0];
                float transYLeft = (float) rangeAxis.valueToJava2D(yleft,
                        dataArea, edge1);
                if (orientation == PlotOrientation.VERTICAL) {
                    left.moveTo(transX1, transY1);
                    left.lineTo(transX1, transStack1);
                    left.lineTo(transXLeft, transStackLeft);
                    left.lineTo(transXLeft, transYLeft);
                }
                else {
                    left.moveTo(transY1, transX1);
                    left.lineTo(transStack1, transX1);
                    left.lineTo(transStackLeft, transXLeft);
                    left.lineTo(transYLeft, transXLeft);
                }
                left.closePath();
            }
            float transStackRight = (float) rangeAxis.valueToJava2D(
                    adjStackRight[0], dataArea, edge1);

            // RIGHT POLYGON
            if (y2 >= 0.0) {
                if (orientation == PlotOrientation.VERTICAL) {
                    right.moveTo(transX1, transStack1);
                    right.lineTo(transX1, transY1);
                    right.lineTo(transXRight, transStackRight);
                }
                else {
                    right.moveTo(transStack1, transX1);
                    right.lineTo(transY1, transX1);
                    right.lineTo(transStackRight, transXRight);
                }
                right.closePath();
            }
            else {
                double yright = (y1 + y2) / 2.0 + stackRight[0];
                float transYRight = (float) rangeAxis.valueToJava2D(yright,
                        dataArea, edge1);
                if (orientation == PlotOrientation.VERTICAL) {
                    right.moveTo(transX1, transStack1);
                    right.lineTo(transX1, transY1);
                    right.lineTo(transXRight, transYRight);
                    right.lineTo(transXRight, transStackRight);
                }
                else {
                    right.moveTo(transStack1, transX1);
                    right.lineTo(transY1, transX1);
                    right.lineTo(transYRight, transXRight);
                    right.lineTo(transStackRight, transXRight);
                }
                right.closePath();
            }
        }

        //  Get series Paint and Stroke
        Paint itemPaint = getItemPaint(series, item, selected);
        if (pass == 0) {
            g2.setPaint(itemPaint);
            g2.fill(left);
            g2.fill(right);
        }

        // add an entity for the item...
        if (entities != null) {
            GeneralPath gp = new GeneralPath(left);
            gp.append(right, false);
            entityArea = gp;
            addEntity(entities, entityArea, dataset, series, item, selected,
                    transX1, transY1);
        }

    }

    /**
     * Calculates the stacked values (one positive and one negative) of all
     * series up to, but not including, <code>series</code> for the specified
     * item. It returns [0.0, 0.0] if <code>series</code> is the first series.
     *
     * @param dataset  the dataset (<code>null</code> not permitted).
     * @param series  the series index.
     * @param index  the item index.
     *
     * @return An array containing the cumulative negative and positive values
     *     for all series values up to but excluding <code>series</code>
     *     for <code>index</code>.
     */
    private double[] getStackValues(TableXYDataset dataset,
                                    int series, int index) {
        double[] result = new double[2];
        for (int i = 0; i < series; i++) {
            double v = dataset.getYValue(i, index);
            if (!Double.isNaN(v)) {
                if (v >= 0.0) {
                    result[1] += v;
                }
                else {
                    result[0] += v;
                }
            }
        }
        return result;
    }

    /**
     * Returns a pair of "stack" values calculated as the mean of the two
     * specified stack value pairs.
     *
     * @param stack1  the first stack pair.
     * @param stack2  the second stack pair.
     *
     * @return A pair of average stack values.
     */
    private double[] averageStackValues(double[] stack1, double[] stack2) {
        double[] result = new double[2];
        result[0] = (stack1[0] + stack2[0]) / 2.0;
        result[1] = (stack1[1] + stack2[1]) / 2.0;
        return result;
    }

    /**
     * Calculates adjusted stack values from the supplied values.  The value is
     * the mean of the supplied values, unless either of the supplied values
     * is zero, in which case the adjusted value is zero also.
     *
     * @param stack1  the first stack pair.
     * @param stack2  the second stack pair.
     *
     * @return A pair of average stack values.
     */
    private double[] adjustedStackValues(double[] stack1, double[] stack2) {
        double[] result = new double[2];
        if (stack1[0] == 0.0 || stack2[0] == 0.0) {
            result[0] = 0.0;
        }
        else {
            result[0] = (stack1[0] + stack2[0]) / 2.0;
        }
        if (stack1[1] == 0.0 || stack2[1] == 0.0) {
            result[1] = 0.0;
        }
        else {
            result[1] = (stack1[1] + stack2[1]) / 2.0;
        }
        return result;
    }

    /**
     * Tests this renderer for equality with an arbitrary object.
     *
     * @param obj  the object (<code>null</code> permitted).
     *
     * @return A boolean.
     */
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof StackedXYAreaRenderer2)) {
            return false;
        }
        StackedXYAreaRenderer2 that = (StackedXYAreaRenderer2) obj;
        if (this.roundXCoordinates != that.roundXCoordinates) {
            return false;
        }
        return super.equals(obj);
    }

    /**
     * Returns a clone of the renderer.
     *
     * @return A clone.
     *
     * @throws CloneNotSupportedException  if the renderer cannot be cloned.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}