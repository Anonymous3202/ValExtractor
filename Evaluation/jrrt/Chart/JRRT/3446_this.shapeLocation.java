package org.jfree.chart.title;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.jfree.chart.block.AbstractBlock;
import org.jfree.chart.block.Block;
import org.jfree.chart.block.LengthConstraintType;
import org.jfree.chart.block.RectangleConstraint;
import org.jfree.chart.util.GradientPaintTransformer;
import org.jfree.chart.util.ObjectUtilities;
import org.jfree.chart.util.PaintUtilities;
import org.jfree.chart.util.PublicCloneable;
import org.jfree.chart.util.RectangleAnchor;
import org.jfree.chart.util.SerialUtilities;
import org.jfree.chart.util.ShapeUtilities;
import org.jfree.chart.util.Size2D;
import org.jfree.chart.util.StandardGradientPaintTransformer;

public class LegendGraphic extends AbstractBlock implements Block, PublicCloneable  {
  final static long serialVersionUID = -1338791523854985009L;
  private boolean shapeVisible;
  private transient Shape shape;
  private RectangleAnchor shapeLocation;
  private RectangleAnchor shapeAnchor;
  private boolean shapeFilled;
  private transient Paint fillPaint;
  private GradientPaintTransformer fillPaintTransformer;
  private boolean shapeOutlineVisible;
  private transient Paint outlinePaint;
  private transient Stroke outlineStroke;
  private boolean lineVisible;
  private transient Shape line;
  private transient Stroke lineStroke;
  private transient Paint linePaint;
  public LegendGraphic(Shape shape, Paint fillPaint) {
    super();
    if(shape == null) {
      throw new IllegalArgumentException("Null \'shape\' argument.");
    }
    if(fillPaint == null) {
      throw new IllegalArgumentException("Null \'fillPaint\' argument.");
    }
    this.shapeVisible = true;
    this.shape = shape;
    this.shapeAnchor = RectangleAnchor.CENTER;
    this.shapeLocation = RectangleAnchor.CENTER;
    this.shapeFilled = true;
    this.fillPaint = fillPaint;
    this.fillPaintTransformer = new StandardGradientPaintTransformer();
    setPadding(2.0D, 2.0D, 2.0D, 2.0D);
  }
  public GradientPaintTransformer getFillPaintTransformer() {
    return this.fillPaintTransformer;
  }
  public Object clone() throws CloneNotSupportedException {
    LegendGraphic clone = (LegendGraphic)super.clone();
    clone.shape = ShapeUtilities.clone(this.shape);
    clone.line = ShapeUtilities.clone(this.line);
    return clone;
  }
  public Object draw(Graphics2D g2, Rectangle2D area, Object params) {
    draw(g2, area);
    return null;
  }
  public Paint getFillPaint() {
    return this.fillPaint;
  }
  public Paint getLinePaint() {
    return this.linePaint;
  }
  public Paint getOutlinePaint() {
    return this.outlinePaint;
  }
  public RectangleAnchor getShapeAnchor() {
    return this.shapeAnchor;
  }
  public RectangleAnchor getShapeLocation() {
    return this.shapeLocation;
  }
  public Shape getLine() {
    return this.line;
  }
  public Shape getShape() {
    return this.shape;
  }
  public Size2D arrange(Graphics2D g2, RectangleConstraint constraint) {
    RectangleConstraint contentConstraint = toContentConstraint(constraint);
    LengthConstraintType w = contentConstraint.getWidthConstraintType();
    LengthConstraintType h = contentConstraint.getHeightConstraintType();
    Size2D contentSize = null;
    if(w == LengthConstraintType.NONE) {
      if(h == LengthConstraintType.NONE) {
        contentSize = arrangeNN(g2);
      }
      else 
        if(h == LengthConstraintType.RANGE) {
          throw new RuntimeException("Not yet implemented.");
        }
        else 
          if(h == LengthConstraintType.FIXED) {
            throw new RuntimeException("Not yet implemented.");
          }
    }
    else 
      if(w == LengthConstraintType.RANGE) {
        if(h == LengthConstraintType.NONE) {
          throw new RuntimeException("Not yet implemented.");
        }
        else 
          if(h == LengthConstraintType.RANGE) {
            throw new RuntimeException("Not yet implemented.");
          }
          else 
            if(h == LengthConstraintType.FIXED) {
              throw new RuntimeException("Not yet implemented.");
            }
      }
      else 
        if(w == LengthConstraintType.FIXED) {
          if(h == LengthConstraintType.NONE) {
            throw new RuntimeException("Not yet implemented.");
          }
          else 
            if(h == LengthConstraintType.RANGE) {
              throw new RuntimeException("Not yet implemented.");
            }
            else 
              if(h == LengthConstraintType.FIXED) {
                contentSize = new Size2D(contentConstraint.getWidth(), contentConstraint.getHeight());
              }
        }
    return new Size2D(calculateTotalWidth(contentSize.getWidth()), calculateTotalHeight(contentSize.getHeight()));
  }
  protected Size2D arrangeNN(Graphics2D g2) {
    Rectangle2D contentSize = new Rectangle2D.Double();
    if(this.line != null) {
      contentSize.setRect(this.line.getBounds2D());
    }
    if(this.shape != null) {
      contentSize = contentSize.createUnion(this.shape.getBounds2D());
    }
    return new Size2D(contentSize.getWidth(), contentSize.getHeight());
  }
  public Stroke getLineStroke() {
    return this.lineStroke;
  }
  public Stroke getOutlineStroke() {
    return this.outlineStroke;
  }
  public boolean equals(Object obj) {
    if(!(obj instanceof LegendGraphic)) {
      return false;
    }
    LegendGraphic that = (LegendGraphic)obj;
    if(this.shapeVisible != that.shapeVisible) {
      return false;
    }
    if(!ShapeUtilities.equal(this.shape, that.shape)) {
      return false;
    }
    if(this.shapeFilled != that.shapeFilled) {
      return false;
    }
    if(!PaintUtilities.equal(this.fillPaint, that.fillPaint)) {
      return false;
    }
    if(!ObjectUtilities.equal(this.fillPaintTransformer, that.fillPaintTransformer)) {
      return false;
    }
    if(this.shapeOutlineVisible != that.shapeOutlineVisible) {
      return false;
    }
    if(!PaintUtilities.equal(this.outlinePaint, that.outlinePaint)) {
      return false;
    }
    if(!ObjectUtilities.equal(this.outlineStroke, that.outlineStroke)) {
      return false;
    }
    if(this.shapeAnchor != that.shapeAnchor) {
      return false;
    }
    if(this.shapeLocation != that.shapeLocation) {
      return false;
    }
    if(this.lineVisible != that.lineVisible) {
      return false;
    }
    if(!ShapeUtilities.equal(this.line, that.line)) {
      return false;
    }
    if(!PaintUtilities.equal(this.linePaint, that.linePaint)) {
      return false;
    }
    if(!ObjectUtilities.equal(this.lineStroke, that.lineStroke)) {
      return false;
    }
    return super.equals(obj);
  }
  public boolean isLineVisible() {
    return this.lineVisible;
  }
  public boolean isShapeFilled() {
    return this.shapeFilled;
  }
  public boolean isShapeOutlineVisible() {
    return this.shapeOutlineVisible;
  }
  public boolean isShapeVisible() {
    return this.shapeVisible;
  }
  public int hashCode() {
    int result = 193;
    result = 37 * result + ObjectUtilities.hashCode(this.fillPaint);
    return result;
  }
  public void draw(Graphics2D g2, Rectangle2D area) {
    area = trimMargin(area);
    drawBorder(g2, area);
    area = trimBorder(area);
    area = trimPadding(area);
    if(this.lineVisible) {
      RectangleAnchor var_3446 = this.shapeLocation;
      Point2D location = RectangleAnchor.coordinates(area, var_3446);
      Shape aLine = ShapeUtilities.createTranslatedShape(getLine(), this.shapeAnchor, location.getX(), location.getY());
      g2.setPaint(this.linePaint);
      g2.setStroke(this.lineStroke);
      g2.draw(aLine);
    }
    if(this.shapeVisible) {
      Point2D location = RectangleAnchor.coordinates(area, this.shapeLocation);
      Shape s = ShapeUtilities.createTranslatedShape(this.shape, this.shapeAnchor, location.getX(), location.getY());
      if(this.shapeFilled) {
        Paint p = this.fillPaint;
        if(p instanceof GradientPaint) {
          GradientPaint gp = (GradientPaint)this.fillPaint;
          p = this.fillPaintTransformer.transform(gp, s);
        }
        g2.setPaint(p);
        g2.fill(s);
      }
      if(this.shapeOutlineVisible) {
        g2.setPaint(this.outlinePaint);
        g2.setStroke(this.outlineStroke);
        g2.draw(s);
      }
    }
  }
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    this.shape = SerialUtilities.readShape(stream);
    this.fillPaint = SerialUtilities.readPaint(stream);
    this.outlinePaint = SerialUtilities.readPaint(stream);
    this.outlineStroke = SerialUtilities.readStroke(stream);
    this.line = SerialUtilities.readShape(stream);
    this.linePaint = SerialUtilities.readPaint(stream);
    this.lineStroke = SerialUtilities.readStroke(stream);
  }
  public void setFillPaint(Paint paint) {
    this.fillPaint = paint;
  }
  public void setFillPaintTransformer(GradientPaintTransformer transformer) {
    if(transformer == null) {
      throw new IllegalArgumentException("Null \'transformer\' argument.");
    }
    this.fillPaintTransformer = transformer;
  }
  public void setLine(Shape line) {
    this.line = line;
  }
  public void setLinePaint(Paint paint) {
    this.linePaint = paint;
  }
  public void setLineStroke(Stroke stroke) {
    this.lineStroke = stroke;
  }
  public void setLineVisible(boolean visible) {
    this.lineVisible = visible;
  }
  public void setOutlinePaint(Paint paint) {
    this.outlinePaint = paint;
  }
  public void setOutlineStroke(Stroke stroke) {
    this.outlineStroke = stroke;
  }
  public void setShape(Shape shape) {
    this.shape = shape;
  }
  public void setShapeAnchor(RectangleAnchor anchor) {
    if(anchor == null) {
      throw new IllegalArgumentException("Null \'anchor\' argument.");
    }
    this.shapeAnchor = anchor;
  }
  public void setShapeFilled(boolean filled) {
    this.shapeFilled = filled;
  }
  public void setShapeLocation(RectangleAnchor location) {
    if(location == null) {
      throw new IllegalArgumentException("Null \'location\' argument.");
    }
    this.shapeLocation = location;
  }
  public void setShapeOutlineVisible(boolean visible) {
    this.shapeOutlineVisible = visible;
  }
  public void setShapeVisible(boolean visible) {
    this.shapeVisible = visible;
  }
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    SerialUtilities.writeShape(this.shape, stream);
    SerialUtilities.writePaint(this.fillPaint, stream);
    SerialUtilities.writePaint(this.outlinePaint, stream);
    SerialUtilities.writeStroke(this.outlineStroke, stream);
    SerialUtilities.writeShape(this.line, stream);
    SerialUtilities.writePaint(this.linePaint, stream);
    SerialUtilities.writeStroke(this.lineStroke, stream);
  }
}