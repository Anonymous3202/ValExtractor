/* ===========================================================
 * JFreeChart : a free chart library for the Java(tm) platform
 * ===========================================================
 *
 * (C) Copyright 2000-2008, by Object Refinery Limited and Contributors.
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
 * --------------------
 * TextBlockAnchor.java
 * --------------------
 * (C) Copyright 2003-2008, by Object Refinery Limited.
 *
 * Original Author:  David Gilbert (for Object Refinery Limited);
 * Contributor(s):   -;
 *
 * Changes:
 * --------
 * 06-Nov-2003 : Version 1 (DG);
 * 22-Mar-2004 : Added readResolve() method (DG);
 * 20-Jun-2007 : Copied from JCommon (D)G;
 *
 */

package org.jfree.chart.text;

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Used to indicate the position of an anchor point for a text block.
 */
public final class TextBlockAnchor implements Serializable {

    /** For serialization. */
    private static final long serialVersionUID = -3045058380983401544L;

    /** Top/left. */
    public static final TextBlockAnchor TOP_LEFT
            = new TextBlockAnchor("TextBlockAnchor.TOP_LEFT");

    /** Top/center. */
    public static final TextBlockAnchor TOP_CENTER = new TextBlockAnchor(
            "TextBlockAnchor.TOP_CENTER");

    /** Top/right. */
    public static final TextBlockAnchor TOP_RIGHT = new TextBlockAnchor(
           "TextBlockAnchor.TOP_RIGHT");

    /** Middle/left. */
    public static final TextBlockAnchor CENTER_LEFT = new TextBlockAnchor(
            "TextBlockAnchor.CENTER_LEFT");

    /** Middle/center. */
    public static final TextBlockAnchor CENTER = new TextBlockAnchor(
            "TextBlockAnchor.CENTER");

    /** Middle/right. */
    public static final TextBlockAnchor CENTER_RIGHT = new TextBlockAnchor(
            "TextBlockAnchor.CENTER_RIGHT");

    /** Bottom/left. */
    public static final TextBlockAnchor BOTTOM_LEFT
        = new TextBlockAnchor("TextBlockAnchor.BOTTOM_LEFT");

    /** Bottom/center. */
    public static final TextBlockAnchor BOTTOM_CENTER
            = new TextBlockAnchor("TextBlockAnchor.BOTTOM_CENTER");

    /** Bottom/right. */
    public static final TextBlockAnchor BOTTOM_RIGHT
            = new TextBlockAnchor("TextBlockAnchor.BOTTOM_RIGHT");

    /** The name. */
    private String name;

    /**
     * Private constructor.
     *
     * @param name  the name.
     */
    private TextBlockAnchor(String name) {
        this.name = name;
    }

    /**
     * Returns a string representing the object.
     *
     * @return The string.
     */
    public String toString() {
        return this.name;
    }

    /**
     * Returns <code>true</code> if this object is equal to the specified
     * object, and <code>false</code> otherwise.
     *
     * @param obj  the other object.
     *
     * @return A boolean.
     */
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TextBlockAnchor)) {
            return false;
        }

        TextBlockAnchor other = (TextBlockAnchor) obj;
        if (!this.name.equals(other.name)) {
            return false;
        }

        return true;
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return the hashcode
     */
    public int hashCode() {
        return this.name.hashCode();
    }

    /**
     * Ensures that serialization returns the unique instances.
     *
     * @return The object.
     *
     * @throws ObjectStreamException if there is a problem.
     */
    private Object readResolve() throws ObjectStreamException {
        if (this.equals(TextBlockAnchor.TOP_CENTER)) {
            return TextBlockAnchor.TOP_CENTER;
        }
        else if (this.equals(TextBlockAnchor.TOP_LEFT)) {
            return TextBlockAnchor.TOP_LEFT;
        } else {
			TextBlockAnchor var_3281 = TextBlockAnchor.TOP_RIGHT;
			if (this.equals(var_3281)) {
			    return TextBlockAnchor.TOP_RIGHT;
			}
			else if (this.equals(TextBlockAnchor.CENTER)) {
			    return TextBlockAnchor.CENTER;
			}
			else if (this.equals(TextBlockAnchor.CENTER_LEFT)) {
			    return TextBlockAnchor.CENTER_LEFT;
			}
			else if (this.equals(TextBlockAnchor.CENTER_RIGHT)) {
			    return TextBlockAnchor.CENTER_RIGHT;
			}
			else if (this.equals(TextBlockAnchor.BOTTOM_CENTER)) {
			    return TextBlockAnchor.BOTTOM_CENTER;
			}
			else if (this.equals(TextBlockAnchor.BOTTOM_LEFT)) {
			    return TextBlockAnchor.BOTTOM_LEFT;
			}
			else if (this.equals(TextBlockAnchor.BOTTOM_RIGHT)) {
			    return TextBlockAnchor.BOTTOM_RIGHT;
			}
		}
        return null;
    }

}
